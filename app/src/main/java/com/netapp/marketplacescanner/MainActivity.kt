package com.netapp.marketplacescanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.netapp.marketplacescanner.ui.AppRoot
import com.netapp.marketplacescanner.ui.theme.MarketplaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarketplaceTheme {
                AppRoot()
            }
        }
    }
}
