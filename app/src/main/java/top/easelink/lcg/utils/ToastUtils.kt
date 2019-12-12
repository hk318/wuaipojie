package top.easelink.lcg.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import top.easelink.lcg.LCGApp


fun showMessage(msg: String) {
    if (isOnMainThread()) {
        Toast.makeText(LCGApp.getContext(), msg, Toast.LENGTH_SHORT).show()
    } else {
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(LCGApp.getContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

}

fun showMessage(@StringRes resource: Int) {
    if (isOnMainThread()) {
        Toast.makeText(LCGApp.getContext(), resource, Toast.LENGTH_SHORT).show()
    } else {
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(LCGApp.getContext(), resource, Toast.LENGTH_SHORT).show()
        }
    }
}

fun showMessage(context: Context, msg: String) {
    if (isOnMainThread()) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    } else {
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}