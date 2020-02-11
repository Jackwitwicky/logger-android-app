package com.jacknkiarie.logger

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import com.jacknkiarie.signinui.models.SignInUI
import java.util.*
import org.json.JSONException
import com.android.volley.VolleyError
import com.android.volley.Request.Method.POST
import com.android.volley.toolbox.JsonObjectRequest
import android.R.attr.phoneNumber
import android.app.ProgressDialog
import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import org.json.JSONObject


class SplashActivity : AppCompatActivity() {

    private val SPLASH_TIME_OUT : Long = 3000
    private val DEFAULT_APP_ID_VALUE = "default"

    private val KEY_EMAIL = "email"
    private val KEY_APP_ID = "app_id"
    private val KEY_SIGN_IN_TYPE = "sign_in_type"
    private val LOG_URL = "https://sign-in-logger.herokuapp.com/logs.json"
    private val TAG = SplashActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler().postDelayed(Runnable {
            // open the sign in ui
            SignInUI.Builder(this)
                .setSignInType(SignInUI.EMAIL_PASSWORD_FORM)
                .setPinSignInEnabled(true)
                .setFingerprintSignInEnabled(true)
                .setPasswordLength(6)
                .setTitle("Logger")
                .setSubtitle("The best log tracking app")
                .build()

        }, SPLASH_TIME_OUT)
    }

    private fun getOrCreateAppID(): String {
        val sharedPref = getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        var savedAppId =
            sharedPref.getString(getString(R.string.saved_app_id_key), DEFAULT_APP_ID_VALUE).toString()

        if (savedAppId == DEFAULT_APP_ID_VALUE) {
            savedAppId = UUID.randomUUID().toString()
            with (sharedPref.edit()) {
                putString(getString(R.string.saved_app_id_key), savedAppId)
                apply()
            }
        }

        return savedAppId
    }

    private fun generateRequestBody(appId: String, data: Intent): JSONObject {
        val signInType = data.getStringExtra(SignInUI.PARAM_SIGN_IN_TYPE)
        val obj = JSONObject()
        obj.put(KEY_APP_ID, appId)
        obj.put(KEY_SIGN_IN_TYPE, signInType)
        if (signInType == SignInUI.EMAIL_PASSWORD_FORM) {
            obj.put(KEY_EMAIL, data.getStringExtra(SignInUI.PARAM_EMAIL))
        }

        return JSONObject().put("log", obj) // wrap the request in an object called log to allow rails to parse it
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SignInUI.REQUEST_CODE) {
            if (resultCode == SignInUI.RESULT_OK) {

                var savedAppId = getOrCreateAppID()

                // send the data to the back end
                var requestBody = generateRequestBody(savedAppId, data!!)
                submitLog(requestBody)
            }
            else {
                // the user has opted out of the sign in process. Close the app
                finish()
            }
        }
    }

    // method to sign up client
    private fun submitLog(requestBody: JSONObject) {
        var loading = ProgressDialog(this)
        loading.setTitle("Please wait...")
        loading.setMessage("Verifying")
        loading.show()

        try {
            val jsonObjectRequest = JsonObjectRequest(
                POST,
                LOG_URL,
                requestBody,
                object : Response.Listener<JSONObject> {

                    override fun onResponse(response: JSONObject) {
                        // analyze server response
                        Log.d(TAG, response.toString())

                        try {
                            loading.dismiss()
                            // check if data was stored successfully and open app
                            if (response.has("id")) {
                                val mainIntent = Intent(this@SplashActivity, MainActivity::class.java)
                                startActivity(mainIntent)
                            }
                            else {
                                // reopen sign in ui
                                SignInUI.Builder(this@SplashActivity)
                                    .setSignInType(SignInUI.EMAIL_PASSWORD_FORM)
                                    .setPinSignInEnabled(true)
                                    .setFingerprintSignInEnabled(true)
                                    .setPasswordLength(6)
                                    .setTitle("Logger")
                                    .setSubtitle("The best log tracking app")
                                    .build()
                            }

                        } catch (e: JSONException) {
                            e.printStackTrace()
                            loading.dismiss()
                        }

                    }
                },
                object : Response.ErrorListener {

                    override fun onErrorResponse(error: VolleyError) {
                        error.printStackTrace()
                        loading.dismiss()
                    }
                })

            // Access the RequestQueue through your singleton class.
            VolleySingleton.getInstance(this).addToRequestQueue(jsonObjectRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }
}
