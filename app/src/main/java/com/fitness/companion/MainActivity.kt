package com.fitness.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitness.companion.presentation.navigation.FitnessCompanionApp
import com.fitness.companion.presentation.theme.FitnessCompanionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitnessCompanionTheme {
                FitnessCompanionApp()
            }
        }
    }
}
