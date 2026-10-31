package com.swpp.team5.frameless

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swpp.team5.frameless.data.AuthRepository
import com.swpp.team5.frameless.data.NewsRepository
import com.swpp.team5.frameless.ui.FrameLESSApp
import com.swpp.team5.frameless.ui.viewmodel.AuthViewModel
import com.swpp.team5.frameless.ui.viewmodel.NewsViewModel

/** 앱 시작만 담당한다. 화면과 상태는 ui 패키지에 있다. */
class MainActivity : ComponentActivity() {
    private val newsViewModel: NewsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NewsViewModel(NewsRepository(applicationContext)) as T
        }
    }
    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AuthViewModel(AuthRepository(applicationContext)) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FrameLESSApp(newsViewModel, authViewModel) }
    }
}
