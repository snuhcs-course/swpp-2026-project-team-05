package com.swpp.team5.frameless

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.swpp.team5.frameless.ui.FrameLESSApp
import com.swpp.team5.frameless.ui.viewmodel.AnalysisViewModel

/** 앱 시작만 담당한다. 화면과 상태는 ui 패키지에 있다. */
class MainActivity : ComponentActivity() {
    private val viewModel: AnalysisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FrameLESSApp(viewModel) }
    }
}
