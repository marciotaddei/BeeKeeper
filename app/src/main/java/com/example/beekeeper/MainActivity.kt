package com.example.beekeeper

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.BufferedReader
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import androidx.core.graphics.toColorInt
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var centerLetterInput: EditText
    private lateinit var otherLettersInput: EditText
    private lateinit var resultsTable: TableLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.navigationBarColor = "#F7DA21".toColorInt()
        window.statusBarColor = "#F7DA21".toColorInt()

        val menuButton = findViewById<Button>(R.id.menuButton)
        val loadButton   = findViewById<Button>(R.id.NYTLoadButton)
        val searchButton = findViewById<Button>(R.id.searchButton)

        val mainScreen = findViewById<LinearLayout>(R.id.mainScreen)
        val fragmentContainer = findViewById<FrameLayout>(R.id.fragment_container)

        centerLetterInput = findViewById(R.id.singleCharInput)
        otherLettersInput = findViewById(R.id.manyCharsInput)
        resultsTable = findViewById(R.id.resultsTable)

        val hint = SpannableString("Center\nletter")
        hint.setSpan(AbsoluteSizeSpan(14, true),
            0, hint.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        centerLetterInput.hint = hint

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
            }
        }


        loadButton.setOnClickListener {
            centerLetterInput.setText("")
            otherLettersInput.setText("")
            resultsTable.removeAllViews()
            val showToast: Toast? = Toast.makeText(
                this, "Fetching...", Toast.LENGTH_LONG).apply { show() }
            prefs.edit { remove("min_chars") }

            var beeList : List<String>
            lifecycleScope.launch {
                beeList = getNytBeeWords()
                val (center, others) = getLettersFromSolution(beeList)

                centerLetterInput.setText(center.uppercase())
                otherLettersInput.setText(others.uppercase())

                searchButton.performClick()
                showToast?.cancel()
            }
        }

        searchButton.setOnClickListener {
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

            val (matches, nTotal) = findMatches(centerLetterInput, otherLettersInput,
                                                minChars, dictionaryWords)
            val nMatches = printTable(resultsTable, matches, nTotal)
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
            if (hasFocus) {(view as EditText).selectAll()}
        }
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

    private suspend fun getNytBeeWords(): List<String> = withContext(Dispatchers.IO) {
        val url = "https://nytbee.com/"
        try {val doc = Jsoup.connect(url).get()
            val elements = doc.select("div[id^=word-div-]")
            elements.mapNotNull {
                val id = it.id() // example: "word-div-honey"
                val prefix = "word-div-"
                if (id.startsWith(prefix)) id.removePrefix(prefix) else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun getLettersFromSolution(solution: List<String>): Pair<String, String> {
        val allLetters = mutableSetOf<Char>()
        val centerCandidates = "abcdefghijklmnopqrstuvwxyz".toCharArray().toMutableSet()
        for (word in solution){
            val wSet = word.toSet()
            allLetters.addAll(wSet)
            centerCandidates.retainAll(wSet)
            if (allLetters.size == 7 && centerCandidates.size ==1){
                allLetters.remove(centerCandidates.first())
                break}
        }
        return centerCandidates.joinToString("") to allLetters.joinToString("")
    }


    private fun findMatches(centerLetter: EditText, otherLetters: EditText,
                            minChars:Int, wordList: List<String>):
            Pair<List<Pair<String, Int>>,Int> {
        val cntLetter = centerLetter.text.toString().lowercase()
        val input = otherLetters.text.toString().lowercase()
        val inputSet = (cntLetter+input).toSet()

        var outList = wordList.filter { word ->
            word.length >= minChars && word.toSet().containsAll(cntLetter.toSet())
                    && inputSet.containsAll(word.toSet())}
        outList = outList.sortedBy{-it.toSet().size}.map{it.uppercase()}

        val outWnChars = outList.map { it to it.toSet().size }
        return Pair(outWnChars, (cntLetter+input).toSet().size)
    }

    fun clearTableLetters() {
        resultsTable.removeAllViews()
        centerLetterInput.setText("")
        otherLettersInput.setText("")
    }

    private fun printTable(table: TableLayout, matches: List<Pair<String,Int>>, nLetters: Int): Int {
        val pangramColor = 0xFFFF0000.toInt()
        val beeYellow    = 0xFFF7DA21.toInt()
        for ((i, match) in matches.withIndex()) {
            val pangramFlag = (match.second == nLetters)
            var textColor = 0xFF000000.toInt()
            if(pangramFlag){textColor = pangramColor}
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
                text = if(pangramFlag){"Pangram!"} else{""}
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
