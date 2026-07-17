package me.mudkip.moememos.ui.page.common

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.math.abs
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.mudkip.moememos.MainActivity
import me.mudkip.moememos.data.model.ShareContent
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.account.AccountPage
import me.mudkip.moememos.ui.page.account.AddAccountPage
import me.mudkip.moememos.ui.page.login.LoginPage
import me.mudkip.moememos.ui.page.memoinput.MemoInputPage
import me.mudkip.moememos.ui.page.memos.MemoDetailPage
import me.mudkip.moememos.ui.page.memos.MemosPage
import me.mudkip.moememos.ui.page.memos.SearchPage
import me.mudkip.moememos.ui.page.memos.TagMemoPage
import me.mudkip.moememos.ui.page.resource.ResourceListPage
import me.mudkip.moememos.ui.page.settings.SettingsPage
import me.mudkip.moememos.ui.theme.MoeMemosTheme
import me.mudkip.moememos.viewmodel.LocalUserState

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val userStateViewModel = LocalUserState.current
    val context = LocalContext.current
    var shareContent by remember { mutableStateOf<ShareContent?>(null) }

    // 右滑返回（Telos 手感）：编辑器等模态页除外，阈值触发 + 震动
    val haptic = LocalHapticFeedback.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val modalRoutes = setOf(RouteName.INPUT, RouteName.SHARE)
    val swipeBackEnabled = navController.previousBackStackEntry != null &&
            modalRoutes.none { currentRoute == it } &&
            currentRoute?.startsWith(RouteName.EDIT) != true
    val swipeThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }

    CompositionLocalProvider(LocalRootNavController provides navController) {
        MoeMemosTheme {
            Box(
                Modifier.pointerInput(swipeBackEnabled) {
                    if (!swipeBackEnabled) return@pointerInput
                    var totalDx = 0f
                    var totalDy = 0f
                    var fired = false
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f; totalDy = 0f; fired = false },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDx += dragAmount
                            totalDy += change.positionChange().y
                            if (!fired && totalDx > swipeThresholdPx && totalDx > abs(totalDy) * 2) {
                                fired = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.popBackStack()
                            }
                        }
                    )
                }
            ) {
            NavHost(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                navController = navController,
                startDestination = RouteName.MEMOS,
                // 横向过场：进入从右滑入，返回向右滑出（配合右滑返回手势）
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start,
                        initialOffset = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start,
                        targetOffset = { it / 4 }) + fadeOut()
                },
                popEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End,
                        initialOffset = { it / 4 }) + fadeIn()
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End,
                        targetOffset = { it }) + fadeOut()
                },
            ) {
                composable(RouteName.MEMOS) {
                    MemosPage()
                }

                composable(RouteName.SETTINGS) {
                    SettingsPage(navController = navController)
                }

                composable(RouteName.ADD_ACCOUNT) {
                    AddAccountPage(navController = navController)
                }

                composable(RouteName.LOGIN) {
                    LoginPage(navController = navController)
                }

                composable(RouteName.INPUT,
                    enterTransition = modalEnter, popExitTransition = modalExit) {
                    MemoInputPage()
                }

                composable(RouteName.SHARE,
                    enterTransition = modalEnter, popExitTransition = modalExit) {
                    MemoInputPage(shareContent = shareContent)
                }

                composable("${RouteName.EDIT}?memoId={id}",
                    enterTransition = modalEnter, popExitTransition = modalExit
                ) { entry ->
                    MemoInputPage(memoIdentifier = entry.arguments?.getString("id"))
                }

                composable(RouteName.RESOURCE) {
                    ResourceListPage(navController = navController)
                }

                composable("${RouteName.ACCOUNT}?accountKey={accountKey}") { entry ->
                    AccountPage(
                        navController = navController,
                        selectedAccountKey = entry.arguments?.getString("accountKey") ?: ""
                    )
                }

                composable(RouteName.SEARCH) {
                    SearchPage(navController = navController)
                }

                composable("${RouteName.TAG}/{tag}") { entry ->
                    val tag = entry.arguments?.getString("tag")?.let(Uri::decode) ?: ""
                    TagMemoPage(tag = tag, navController = navController)
                }

                composable("${RouteName.MEMO_DETAIL}?memoId={memoId}") { entry ->
                    val memoId = entry.arguments?.getString("memoId")
                    if (memoId != null) {
                        MemoDetailPage(navController = navController, memoIdentifier = Uri.decode(memoId))
                    }
                }
            }
            }
        }
    }


    LaunchedEffect(Unit) {
        if (!userStateViewModel.hasAnyAccount()) {
            if (navController.currentDestination?.route != RouteName.ADD_ACCOUNT) {
                navController.navigate(RouteName.ADD_ACCOUNT) {
                    popUpTo(navController.graph.id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }
        userStateViewModel.loadCurrentUser()
    }

    fun handleIntent(intent: Intent) {
        when(intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                shareContent = ShareContent.parseIntent(intent)
                navController.navigate(RouteName.SHARE)
            }
            Intent.ACTION_VIEW -> {
                when (intent.getStringExtra("action")) {
                    "compose" -> navController.navigate(RouteName.INPUT)
                    "search" -> navController.navigate(RouteName.SEARCH)
                }
            }
            MainActivity.ACTION_NEW_MEMO -> {
                navController.navigate(RouteName.INPUT)
            }
            MainActivity.ACTION_EDIT_MEMO -> {
                val memoId = intent.getStringExtra(MainActivity.EXTRA_MEMO_ID)
                if (memoId != null) {
                    navController.navigate("${RouteName.EDIT}?memoId=$memoId")
                }
            }
            MainActivity.ACTION_VIEW_MEMO -> {
                val memoId = intent.getStringExtra(MainActivity.EXTRA_MEMO_ID)
                if (memoId != null) {
                    navController.navigate("${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(memoId)}")
                }
            }
        }
    }

    LaunchedEffect(context) {
        if (context is ComponentActivity && context.intent != null) {
            handleIntent(context.intent)
        }
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity

        val listener = Consumer<Intent> {
            handleIntent(it)
        }

        activity?.addOnNewIntentListener(listener)

        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }
}

val LocalRootNavController =
    compositionLocalOf<NavHostController> { error(me.mudkip.moememos.R.string.nav_host_controller_not_found.string) }

// 编辑器一类的模态页：保留上滑弹出 / 下滑收起的过场
private val modalEnter: (AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> EnterTransition?) = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up,
        initialOffset = { it / 4 }) + fadeIn()
}
private val modalExit: (AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> ExitTransition?) = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down,
        targetOffset = { it / 4 }) + fadeOut()
}
