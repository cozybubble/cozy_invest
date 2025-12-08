package com.example.h4

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val stockList = mutableListOf<Stock>()
    private lateinit var fabAddStock: FloatingActionButton
    private lateinit var fabRefresh: FloatingActionButton
    private val STOCK_DATA_FILE = "stock_data.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 初始化
        listView = findViewById(R.id.listView)
        fabAddStock = findViewById(R.id.fabAddStock)
        fabRefresh = findViewById(R.id.fabRefresh)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 设置FAB点击事件
        fabAddStock.setOnClickListener {
            showAddStockDialog()
        }
        fabRefresh.setOnClickListener {
            refreshStockInfo()
        }

        loadStockData()
    }

    private fun refreshStockInfo() {
        if (stockList.isEmpty()) {
            Toast.makeText(this, "暂无股票数据", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val deferredList = stockList.map { stockItem ->
                async {
                    try {
                        getNewStockInfo(stockItem.code)?.let{(_,price) ->
                            val newChangeRate = ((price / 1000 - stockItem.buyPrice) / stockItem.buyPrice) * 100
                            stockItem.copy(changeRate = newChangeRate)
                        }
                    } catch (e: Exception) {
                        Log.e("StockUpdate", "Failed to fetch ${stockItem.code}", e)
                        null
                    }
                }
            }

            // 等待所有异步任务完成
            val updatedList = deferredList.awaitAll().filterNotNull()

            if (updatedList.isNotEmpty()) {
                stockList.clear()          // 清空原有数据
                stockList.addAll(updatedList)  // 添加新数据
                withContext(Dispatchers.Main) {
                    setupListView()
                    Toast.makeText(this@MainActivity, "刷新成功", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "刷新失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEditPriceDialog(stock: Stock) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_price, null)
        val etPrice = dialogView.findViewById<EditText>(R.id.etPrice)
        etPrice.setText(stock.buyPrice.toString())

        AlertDialog.Builder(this)
            .setTitle("修改买入价格")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val priceStr = etPrice.text.toString().trim()
                if (priceStr.isEmpty()) {
                    Toast.makeText(this, "请输入价格", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    val newPrice = priceStr.toDouble()
                    if (newPrice <= 0) {
                        Toast.makeText(this, "价格必须大于0", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // 更新价格并重新计算涨跌幅
                    val index = stockList.indexOfFirst { it.code == stock.code }
                    if (index != -1) {
                        lifecycleScope.launch {
                            getNewStockInfo(stock.code)?.let { (_, currentPrice) ->
                                stockList[index] = stock.copy(
                                    buyPrice = newPrice,
                                    changeRate = ((currentPrice / 1000 - newPrice) / newPrice) * 100
                                )
                                withContext(Dispatchers.Main) {
                                    setupListView()
                                }
                            }
                        }
                    }
                    saveStockData() // 保存修改后的数据
                    Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "请输入有效的价格", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadStockData() {
        lifecycleScope.launch {
            try {
                val file = File(filesDir, STOCK_DATA_FILE)
                if (file.exists()) {
                    val jsonString = file.readText()
                    val jsonArray = JSONArray(jsonString)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val code = item.getString("code")
                        val buyPrice = item.getDouble("buyPrice")
                        addStockItem(code, buyPrice)
                    }
                }
            } catch (e: Exception) {
                Log.e("StockData", "Error loading stock data", e)
            }
        }
    }

    private fun saveStockData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                stockList.forEach { stock ->
                    val item = JSONObject().apply {
                        put("code", stock.code)
                        put("buyPrice", stock.buyPrice)
                    }
                    jsonArray.put(item)
                }

                val file = File(filesDir, STOCK_DATA_FILE)
                FileWriter(file).use { writer ->
                    writer.write(jsonArray.toString())
                }
            } catch (e: IOException) {
                Log.e("StockData", "Error saving stock data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "保存数据失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteStockItem(code: String) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这只股票吗？")
            .setPositiveButton("确定") { _, _ ->
                stockList.removeAll { it.code == code }
                listView.adapter = StockAdapter(this, stockList,{ code ->
                    deleteStockItem(code)
                },{ stock -> showEditPriceDialog(stock) })
                saveStockData()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddStockDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stock, null)
        val etStockCode = dialogView.findViewById<EditText>(R.id.etStockCode)
        val etBuyPrice = dialogView.findViewById<EditText>(R.id.etBuyPrice)

        AlertDialog.Builder(this)
            .setTitle("添加股票")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val code = etStockCode.text.toString().trim()
                val priceStr = etBuyPrice.text.toString().trim()

                if (code.isEmpty() || priceStr.isEmpty()) {
                    Toast.makeText(this, "请输入完整的股票信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    val price = priceStr.toDouble()
                    if (price <= 0) {
                        Toast.makeText(this, "买入价格必须大于0", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    lifecycleScope.launch {
                        addStockItem(code, price)
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "请输入有效的买入价格", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun addStockItem(code: String, buyPrice: Double) {
        lifecycleScope.launch {
            var data = getNewStockInfo(code);
            data?.let { (name, price) ->
                // 使用股票名称和价格
                stockList.add(
                    Stock(
                        code,
                        name,
                        buyPrice,
                        (price / 1000 - buyPrice) / buyPrice * 100
                    )
                )
            }
            withContext(Dispatchers.Main) {
                setupListView()
            }
            saveStockData()
        }
    }

    private fun setupListView() {
        listView.adapter = StockAdapter(
            this@MainActivity,
            stockList,
            { code -> deleteStockItem(code) },
            { stock -> showEditPriceDialog(stock) } // 添加价格点击回调
        )
    }

    suspend fun getNewStockInfo(code: String): Pair<String, Double>? = withContext(
        Dispatchers.IO
    ) {
        val prefix = if (code.startsWith("6") || code.startsWith("5")
        ) "1" else "0" // 沪市1，其他（深市）0
        val secid = "$prefix.$code"
        val url = "https://push2delay.eastmoney.com/api/qt/stock/get?secid=$secid&fields=f58,f43"

        return@withContext try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val json = response.body?.string()?.let { JSONObject(it) }
                val data = json?.optJSONObject("data") ?: return@withContext null

                val name = data.optString("f58", "")
                val price = data.optDouble("f43", 0.0)

                if (name.isNotEmpty() && price != 0.0) {
                    Pair(name, price)
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}