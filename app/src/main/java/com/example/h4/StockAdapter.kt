package com.example.h4

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class StockAdapter(
    private val context: Context,
    private val stockList: List<Stock>,
    private val onDeleteClick: (String) -> Unit,
    private val onPriceClick: (Stock) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = stockList.size
    override fun getItem(position: Int): Any = stockList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_stock, parent, false)
        val stock = stockList[position]

        view.findViewById<TextView>(R.id.tvStockName).text = "${stock.name}\n(${stock.code})"
        view.findViewById<TextView>(R.id.tvStockPrice).text = "%.3f".format(stock.buyPrice)

        val changeRate = stock.changeRate
        val changeRateText = "%.2f%%".format(changeRate)
        view.findViewById<TextView>(R.id.tvStockChangeRate).text = changeRateText

        // 根据涨跌幅设置颜色
        val color = when {
            changeRate > 0 -> ContextCompat.getColor(context, R.color.red)
            changeRate < 0 -> ContextCompat.getColor(context, R.color.green)
            else -> ContextCompat.getColor(context, R.color.gray)
        }
        view.findViewById<TextView>(R.id.tvStockChangeRate).setTextColor(color)

        // 设置删除按钮点击事件
        view.findViewById<MaterialButton>(R.id.btn_delete).setOnClickListener {
            onDeleteClick(stock.code)
        }

        // 价格点击事件
        view.findViewById<TextView>(R.id.tvStockPrice).setOnClickListener {
            onPriceClick(stock)
        }
        return view
    }
}