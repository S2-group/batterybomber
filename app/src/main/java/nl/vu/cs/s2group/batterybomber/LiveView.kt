package nl.vu.cs.s2group.batterybomber

import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.*
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import timber.log.Timber
import java.lang.StrictMath.abs
import java.util.*
import android.os.PowerManager
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.LegendRenderer


/**
 * A simple [Fragment] subclass.
 * Use the [LiveView.newInstance] factory method to
 * create an instance of this fragment.
 */
class LiveView : Fragment(R.layout.fragment_live_view) {
    private lateinit var batteryManager: BatteryManager
    private lateinit var powerManager: PowerManager
    private lateinit var broadcastReceiver: BroadcastReceiver
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private val timeLength = 120 //seconds
    private var graphNextXValue = 0.0
    private var lastKnownVoltage : Int = 0    // milliVolts
    private var lastknownLevel : Double = 0.0 // percentage

    private val wattSeries   : LineGraphSeries<DataPoint> = LineGraphSeries()
    private val currentSeries: LineGraphSeries<DataPoint> = LineGraphSeries()
    private lateinit var textView: TextView

    private val mGraphUpdater = object : Runnable {
        private val mInterval = 1000 // milliseconds
        private val maxDataPoints = ((1000/mInterval.toDouble()) * 60 * 5).toInt() // Keep a record of 5 minutes

        override fun run() {
            val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) //Instantaneous battery current in microamperes
            val currentAverage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) //Average battery current in microamperes
            val watts = if(currentNow > 0)  0.0 else (lastKnownVoltage.toDouble() / 1000) * (abs(currentNow).toDouble()/1000/1000) //Only negative current means discharging

            val energy   = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) //Remaining energy in nanowatt-hours
            val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) //Remaining battery capacity in microampere-hours
            val capacityPercentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) //Remaining battery capacity as an integer percentage of total capacity

            /*
             * currentAverage always reports 0
             * energy         always reports 0
             * capacityPercentage == lastknownLevel
             * Usable metrics: currentNow, watts, capacity
             */

            textView.text = "%d mA, %d mV, %.2f W\n%d%%, %d mAH remaining".format(
                currentNow/1000, lastKnownVoltage, watts, lastknownLevel.toInt(), capacity/1000
            )

            wattSeries.appendData(DataPoint(graphNextXValue, watts), graphNextXValue > timeLength, maxDataPoints)
            currentSeries.appendData(DataPoint(graphNextXValue, if(currentNow > 0) 0.0 else (abs(currentNow)/1000).toDouble()), graphNextXValue > timeLength, maxDataPoints)
            graphNextXValue++

            mHandler.postDelayed(this, mInterval.toLong())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val wattGraph    = view.findViewById(R.id.liveGraphWatts    ) as GraphView
        val currentGraph = view.findViewById(R.id.liveGraphCurrent  ) as GraphView
        textView         = view.findViewById(R.id.energyInfoTextView) as TextView

        batteryManager = requireContext().getSystemService(BATTERY_SERVICE) as BatteryManager
        powerManager   = requireContext().getSystemService(POWER_SERVICE  ) as PowerManager
        broadcastReceiver = BatteryManagerBroadcastReceiver { intent ->
            lastKnownVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            lastknownLevel = (level * 100).toDouble() / scale
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        requireContext().registerReceiver(broadcastReceiver, filter)


        wattGraph.addSeries(wattSeries)
        wattGraph.title = "Watt consumption (W)"
        wattGraph.viewport.isXAxisBoundsManual = true;
        wattGraph.viewport.setMinX(0.0);
        wattGraph.viewport.setMaxX(timeLength.toDouble());

        wattGraph.viewport.isYAxisBoundsManual = true;
        wattGraph.viewport.setMinY(0.0);
        wattGraph.viewport.setMaxY(8.0);
        wattGraph.gridLabelRenderer.isHorizontalLabelsVisible = false
        wattGraph.gridLabelRenderer.reloadStyles()

        currentGraph.addSeries(currentSeries)
        currentGraph.title = "Current discharge (mA)"
        currentGraph.viewport.isXAxisBoundsManual = true;
        currentGraph.viewport.setMinX(0.0);
        currentGraph.viewport.setMaxX(timeLength.toDouble());

        currentGraph.viewport.isYAxisBoundsManual = true;
        currentGraph.viewport.setMinY(0.0);
        currentGraph.viewport.setMaxY(2500.0);
        currentGraph.gridLabelRenderer.reloadStyles()

        mGraphUpdater.run()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
        mHandler.removeCallbacks(mGraphUpdater);
        Timber.d("LiveView destroyed!")
    }
}

private class BatteryManagerBroadcastReceiver(
    private val onReceiveIntent: (Intent) -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        onReceiveIntent(intent)
    }
}
