package edu.umb.cs443

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private val geoQuery= "https://api.openweathermap.org/geo/1.0/direct?q="
    private val apikey = "&appid=43537ee5cb4348e43f41344354cb2ef8"
    private val weatherQuery = "https://api.openweathermap.org/data/3.0/onecall?"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val mFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mFragment.getMapAsync(this)

        findViewById<Button>(R.id.button).setOnClickListener{
            getWeatherInfo()
        }
        val edittext = findViewById<View>(R.id.editText) as EditText
        edittext.setOnKeyListener(View.OnKeyListener { v, keyCode, event -> // If the event is a key-down event on the "enter" button
            if (event.action == KeyEvent.ACTION_DOWN &&
                keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                // Perform action on key press
                try {
                    getWeatherInfo()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                return@OnKeyListener true
            }
            false
        })
    }

    override fun onMapReady(map: GoogleMap) {
        mMap = map
    }

    fun getWeatherInfo(){
        val myedittext = findViewById<View>(R.id.editText) as EditText
        var cityname = myedittext.text.toString() ?: return
        myedittext.clearFocus()
        val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCap = connMgr.getNetworkCapabilities(connMgr.activeNetwork)
        if (networkCap != null && (networkCap.hasTransport(TRANSPORT_WIFI)||networkCap.hasTransport(
                TRANSPORT_CELLULAR))) {
            var query = geoQuery+cityname+",us&limit=1"+apikey
            Log.d(DEBUG_TAG, "The query URL is: $query")
            // Log.i("MyTag", query);
            GlobalScope.launch {
                var jStr = downloadUrl(query)
                Log.d(DEBUG_TAG, "The response is: $jStr")
                val ll: LatLng = processJStr(jStr)!!
                Log.d(DEBUG_TAG, "Lat, Log: ${ll.latitude}, ${ll.longitude}")
                withContext(Dispatchers.Main) {
                    updateMap(ll)
                    getWeather(ll)
                }
            }
        } else {
            Toast.makeText(
                applicationContext,
                "No network connection available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /*process the JSON string from GEO query*/
    fun processJStr(result: String?): LatLng? {
        if (result == null) {
            Log.i(DEBUG_TAG, "weather info is null")
            return null
        }
        val jo = JSONArray(result).get(0) as JSONObject
        val lat = jo.getDouble("lat")
        val lng = jo.getDouble("lon")

        return LatLng(lat,lng)
    }

    /*download an URL object*/
    fun downloadUrl(myurl: String): String {
        var `is`: InputStream? = null
        var result = String()
        Log.d(DEBUG_TAG, "The query URL is: $myurl")

        try {
            val url = URL(myurl)
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 10000
            conn.connectTimeout = 15000
            conn.requestMethod = "GET"
            conn.doInput = true
            // Starts the query
            conn.connect()
            val response = conn.responseCode
            Log.d(DEBUG_TAG, "The response is: $response")
            `is` = conn.inputStream
            val bis = BufferedInputStream(`is`)
            var read = 0
            val bufSize = 512
            val buffer = ByteArray(bufSize)
            while (true) {
                read = bis.read(buffer)
                if (read == -1) {
                    break
                }
                result += String(buffer)
            }
        } catch (e: Exception) {
            println(e)
        } finally {
            if (`is` != null) {
                `is`.close()
                Log.d(DEBUG_TAG, "is is closed")
            }
        }
        return result
    }

    fun getWeather(latLng: LatLng) {
        val query = "${weatherQuery}lat=${latLng.latitude}&lon=${latLng.longitude}&exclude=hourly,daily,minutely${apikey}"
        Log.d(DEBUG_TAG, "Weather Query URL is: $query")
        GlobalScope.launch {
            val jStr = downloadUrl(query)
            Log.d(DEBUG_TAG, "Response: $jStr")
            processWeather(jStr)
        }
    }

    fun processWeather(result: String?) {
        val jsonobj = JSONObject(result)
        val current = jsonobj.getJSONObject("current")
        val kel = current.getDouble("temp")
        val cel = kel - 273.15
        val wArray = current.getJSONArray("weather")
        val wObj = wArray.getJSONObject(0)
        val iconCode = wObj.getString("icon")
        val iconUrl = "https://openweathermap.org/img/wn/${iconCode}@2x.png"

        GlobalScope.launch {
            val bitmap = downloadBitmap(iconUrl)
            withContext(Dispatchers.Main) {
                val tempView = findViewById<TextView>(R.id.textView)
                tempView.text = String.format("%.1fC", cel)

                val weatherImage = findViewById<ImageView>(R.id.imageView)
                weatherImage.setImageBitmap(bitmap)
            }
        }
    }

    fun downloadBitmap(iconUrl: String): Bitmap? {
        var inputStream: InputStream? = null
        try {
            val conn = URL(iconUrl).openConnection() as HttpURLConnection
            conn.doInput = true
            conn.connect()
            inputStream = conn.inputStream
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
        }
        return null
    }

    fun updateMap(latLng: LatLng) {
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(latLng).title("Weather Location"))
        Log.d(DEBUG_TAG, "**Lat, Log: ${latLng.latitude}, ${latLng.longitude}")

        val center = CameraUpdateFactory.newLatLng(latLng)
        val zoom = CameraUpdateFactory.zoomTo(12f)
        mMap.moveCamera(center)
        mMap.animateCamera(zoom)
    }


    companion object {
        const val DEBUG_TAG = "edu.umb.cs443.MYMSG"
    }
}