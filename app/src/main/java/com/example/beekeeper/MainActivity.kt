package com.example.beekeeper

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.Toast
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.io.BufferedReader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var centerLetterInput: EditText
    private lateinit var otherLettersInput: EditText
    private lateinit var resultsTable: TableLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                     View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        setContentView(R.layout.activity_main)

        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        val loadButton   = findViewById<Button>(R.id.NYTLoadButton)
        val searchButton = findViewById<Button>(R.id.searchButton)

        val mainScreen = findViewById<LinearLayout>(R.id.mainScreen)
        val fragmentContainer = findViewById<FrameLayout>(R.id.fragment_container)

        centerLetterInput = findViewById(R.id.singleCharInput)
        otherLettersInput = findViewById(R.id.manyCharsInput)
        resultsTable = findViewById(R.id.resultsTable)

        val hint = SpannableString("Center") //"Center\nletter"
        hint.setSpan(AbsoluteSizeSpan(14, true),
            0, hint.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        centerLetterInput.hint = hint
        centerLetterInput.gravity = Gravity.CENTER

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit{remove("min_chars")}
        prefs.edit{remove("dict_choice")}


        menuButton.setOnClickListener {
            mainScreen.visibility = View.GONE
            fragmentContainer.visibility = View.VISIBLE
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                fragmentContainer.visibility = View.GONE
                mainScreen.visibility = View.VISIBLE
                searchButton.performClick()
            }
        }

        loadButton.setOnClickListener {
            centerLetterInput.setText("")
            otherLettersInput.setText("")
            resultsTable.removeAllViews()
            val showToast: Toast? = Toast.makeText(
                this, "Fetching...", Toast.LENGTH_LONG).apply { show() }
            prefs.edit { remove("min_chars") }

            fetchLatestBeePost(
                onResult = {permalink ->
                    runOnUiThread {
                    val theLetters = permalink.trimEnd('/').substringAfterLast('/')
                        .replace("_","").takeLast(7).uppercase()
                    centerLetterInput.setText(theLetters.take(1))
                    otherLettersInput.setText(theLetters.takeLast(6))
                    showToast?.cancel()
                    searchButton.performClick()
                           }},
                onError = { err ->
                    runOnUiThread {
                        Toast.makeText(this, "Fetch failed\nManual input available",
                            Toast.LENGTH_LONG).show()
                        Log.d("Fetch error", err.message!!)
                        centerLetterInput.setText("")
                        otherLettersInput.setText("")
                    }
                }
            )
        }

        searchButton.setOnClickListener {
            if (centerLetterInput.text.toString()=="" && otherLettersInput.text.toString()=="")
            {return@setOnClickListener}

            if (prefs.getBoolean("hide_keyboard", true)) {
                val imm = getSystemService(INPUT_METHOD_SERVICE)
                        as InputMethodManager
                val tokenView = currentFocus ?: otherLettersInput
                imm.hideSoftInputFromWindow(tokenView.windowToken, 0)
                tokenView.clearFocus()
            }

            resultsTable.removeAllViews()

            val showToast: Toast? = Toast.makeText(
                this, "Loading...", Toast.LENGTH_LONG).apply { show() }

            val minChars = prefs.getInt("min_chars", 4)
            val dictFileName = prefs.getString("dict_choice", "2of4brif_plus.txt")!!
            val dictIdx = resources.getStringArray(R.array.dict_filenames).indexOf(dictFileName)
            val dictLabel = if(dictIdx>=0) resources.getStringArray(R.array.dict_labels)[dictIdx]
                else dictFileName
            val dictionaryWords = loadDictionary(dictFileName)

            Log.d("minChars", minChars.toString())
            Log.d("dict Choice", "$dictFileName $dictLabel")

            val matches = findMatches(centerLetterInput, otherLettersInput,
                                                minChars, dictionaryWords)
            val nMatches = printTable(resultsTable, matches)
            resultsTable.addView(
                TextView(this).apply{
                    text = context.getString(R.string.bottom_text)
                        .format(minChars, dictLabel, nMatches)
                    textSize = 12f
                    gravity = Gravity.CENTER}
            )
            showToast?.cancel()
        }

        otherLettersInput.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {(view as EditText).selectAll()}}

        otherLettersInput.setOnEditorActionListener{ _, actionId, _ ->
            if(actionId == EditorInfo.IME_ACTION_SEND){
                searchButton.performClick()
                true}
            else{false}
        }

    }

    private fun loadDictionary(filename: String): List<String> {
        val inputStream = assets.open(filename)
        val reader = BufferedReader(inputStream.reader())
        return reader.readLines()
    }

    fun fetchLatestBeePost(onResult: (String) -> Unit, onError: (Exception) -> Unit)
    {thread {
            try {// 1) Build client & request
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://www.reddit.com/user/NYTSpellingBeeBot/submitted.json?limit=1")
                    .header("User-Agent", "YourAppName/1.0")
                    .build()
                // 2) Execute synchronously on background thread
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful)
                    {throw Exception("HTTP error ${response.code}")}
                    val body = response.body!!.string()
                    // 3) Parse JSON
                    val postData = JSONObject(body)
                        .getJSONObject("data")
                        .getJSONArray("children")
                        .getJSONObject(0)
                        .getJSONObject("data")

                    Log.d("Fetch", postData.toString())
                    val permalink = postData.getString("permalink")
                    onResult(permalink)
                }
            } catch (e: Exception) {onError(e)}
        }
    }


    private fun findMatches(centerLetter: EditText, otherLetters: EditText,
                            minChars:Int, wordList: List<String>):
            List<Triple<String, Int, Boolean>> {

        val cntLetter = centerLetter.text.toString().lowercase().toSet()
        val inputSet = otherLetters.text.toString().lowercase().toSet().union(cntLetter)

        fun scoreOf(word:String): Pair<Int, Boolean> {
            val extraPoints = if(word.lowercase().toSet() == inputSet) 7 else 0
            if (word.length == minChars) return 1+extraPoints to (extraPoints==7)
            return word.length + extraPoints to (extraPoints==7)
        }

        var outList = wordList.filter { word ->
            word.length >= minChars && word.toSet().containsAll(cntLetter)
                    && inputSet.containsAll(word.toSet())}

        outList = outList.sortedBy{-scoreOf(it).first}.map{it.uppercase()}

        val outWScore = outList.map {Triple(it, scoreOf(it).first, scoreOf(it).second)}
        return outWScore //Triple<word, score, ifPangram
    }

    fun clearTableLetters() {
        resultsTable.removeAllViews()
        centerLetterInput.setText("")
        otherLettersInput.setText("")
    }

    private fun printTable(table: TableLayout, matches: List<Triple<String,Int,Boolean>>): Int {
        val pangramColor = 0xFFFF0000.toInt()
        val beeYellow    = 0xFFF7DA21.toInt()
        for ((i, match) in matches.withIndex()) {
            var textColor = 0xFF000000.toInt()
            if(match.third){textColor = pangramColor}
            else{if(i%2==0){textColor = beeYellow}}
            val tableRow = TableRow(this).apply{
                if (i%2==0) {
                    background = GradientDrawable().apply{
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 36f
                        setColor(0xFF101010.toInt())
                    }
                }
            }
            val wordView = TextView(this).apply{
                text = match.first
                textSize = 32f
                setPadding(0,0,0,0)
                setTextColor(textColor)
                layoutParams = TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {marginStart = 15}
            }
            val ifPangram = TextView(this).apply{
                text = if(match.third){"Pangram!"} else{""}
                textSize = 12f
                maxLines = 1
                setPadding(0,0,0,0)
                setTextColor(pangramColor)
                gravity = Gravity.CENTER
                layoutParams = TableRow.LayoutParams(
                    0,
                    TableRow.LayoutParams.MATCH_PARENT,
                    0f
                )
            }
            val lenView = TextView(this).apply{
                text = match.second.toString()
                textSize = 32f
                setPadding(0,0,0,0)
                setTextColor(textColor)
                gravity = Gravity.END
                layoutParams = TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.MATCH_PARENT,  // important to match row height
                    1f
                ).apply{marginEnd = 15}
            }

            tableRow.addView(wordView)
            tableRow.addView(ifPangram)
            tableRow.addView(lenView)
            table.addView(tableRow)
        }
        return matches.size

    }


}
