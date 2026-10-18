package jp.jphacks.sd2606.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.ChatContext
import jp.jphacks.sd2606.ui.chat.ChatScreen
import jp.jphacks.sd2606.ui.chat.ChatThread
import jp.jphacks.sd2606.ui.chat.ChatViewModel
import jp.jphacks.sd2606.ui.flow.FlowScreen
import jp.jphacks.sd2606.ui.manage.ManageScreen
import jp.jphacks.sd2606.ui.meter.MeterScreen
import jp.jphacks.sd2606.ui.profile.ProfileScreen
import jp.jphacks.sd2606.ui.recommend.RecommendScreen

private object Routes {
    const val PROFILE = "profile"
    const val RECOMMEND = "recommend"
    const val FLOW = "flow/{id}"
    const val METER = "meter"
    const val MANAGE = "manage"
    const val CHAT = "chat"
    const val PROCEDURE_CHAT = "chat/{id}"
    fun flow(id: String) = "flow/$id"
    fun chat(id: String) = "chat/$id"
}

private const val SLIDE_MS = 300

/** 下部のナビゲーションバーで切り替える画面。切り替えは横スライドではなくフェードにする */
private val TAB_ROUTES = setOf(Routes.RECOMMEND, Routes.METER, Routes.CHAT, Routes.MANAGE)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(direction: SlideDirection): EnterTransition =
    if (isTabSwitch()) fadeIn(tween(SLIDE_MS)) else slideIntoContainer(direction, tween(SLIDE_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(direction: SlideDirection): ExitTransition =
    if (isTabSwitch()) fadeOut(tween(SLIDE_MS)) else slideOutOfContainer(direction, tween(SLIDE_MS))

/**
 * ナビゲーションバーの切り替え。バイト代と通知と履歴は手続きの上に1枚だけ重ね、戻ると手続きに戻る
 * （タブ同士を行き来しても画面が積み重ならないように、手続きまで戻してから重ねる）。
 */
private fun NavHostController.switchTab(tab: Tab) {
    val route = when (tab) {
        Tab.PROCEDURES -> {
            popBackStack(Routes.RECOMMEND, inclusive = false)
            return
        }
        Tab.METER -> Routes.METER
        Tab.AI -> Routes.CHAT
        Tab.HISTORY -> Routes.MANAGE
    }
    navigate(route) {
        popUpTo(Routes.RECOMMEND)
        launchSingleTop = true
    }
}

@Composable
fun AppNavHost(vm: AppViewModel = viewModel(), chatVm: ChatViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val threads by chatVm.threads.collectAsStateWithLifecycle()

    // AI に送る情報は、送る直前の回答から作る（聞き返しに答えて回答を保存した直後でも新しい回答を送るため）
    fun chatContext(procedureId: String?): () -> ChatContext = {
        val s = vm.state.value
        val catalog = (s.catalog as? UiResult.Success)?.data
        if (catalog == null) ChatContext(procedureId, emptyList(), emptyList())
        else ChatContext.build(catalog, s.answers, s.evaluation, s.completed, procedureId)
    }

    @Composable
    fun Chat(procedureId: String?, onBack: (() -> Unit)?) {
        val context = chatContext(procedureId)
        ChatScreen(
            state = state,
            procedureId = procedureId,
            thread = threads[procedureId ?: ""] ?: ChatThread(),
            onSend = { chatVm.send(it, context) },
            onPickOption = { reply, option ->
                // アプリの質問項目に当たる聞き返しなら、選んだ値を回答に保存してから答えとして送る
                if (reply.field != null && option.value != null) vm.answer(reply.field, Answer.Single(option.value))
                chatVm.send(option.label, context)
            },
            onRetry = { chatVm.retry(context) },
            onClear = { chatVm.clear(procedureId) },
            onRetryCatalog = vm::loadCatalog,
            onBack = onBack,
            onTab = nav::switchTab,
        )
    }

    // 画面遷移は横スライド: 進むときは右から入って左へ抜け、戻るときはその逆。
    // ジェスチャーで戻るとき（予測型の戻る）は predictivePop* が使われ、指定しないと標準の縮むアニメーションになるので、同じスライドにする
    NavHost(
        navController = nav,
        startDestination = Routes.PROFILE,
        enterTransition = { enter(SlideDirection.Start) },
        exitTransition = { exit(SlideDirection.Start) },
        popEnterTransition = { enter(SlideDirection.End) },
        popExitTransition = { exit(SlideDirection.End) },
        predictivePopEnterTransition = { _ -> enter(SlideDirection.End) },
        predictivePopExitTransition = { _ -> exit(SlideDirection.End) },
    ) {
        composable(Routes.PROFILE) {
            ProfileScreen(
                catalog = state.catalog,
                answers = state.answers,
                onAnswer = vm::answer,
                onRetry = vm::loadCatalog,
                onSubmit = {
                    vm.submitInitial()
                    nav.navigate(Routes.RECOMMEND)
                },
            )
        }
        composable(Routes.RECOMMEND) {
            RecommendScreen(
                state = state,
                onAnswer = vm::answer,
                onComplete = vm::setCompleted,
                onClearAll = {
                    vm.clearAll()
                    nav.popBackStack(Routes.PROFILE, inclusive = false)
                },
                onClearCompleted = vm::clearCompleted,
                onClearDismissed = vm::clearDismissed,
                onDismiss = vm::setDismissed,
                onConfirm = vm::setConfirmed,
                onRetry = vm::loadCatalog,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.flow(id)) },
                onTab = nav::switchTab,
            )
        }
        composable(Routes.METER) {
            MeterScreen(
                birthDate = (state.answers["birth_date"] as? Answer.Date)?.let { runCatching { it.toLocalDate() }.getOrNull() },
                onTab = nav::switchTab,
            )
        }
        composable(Routes.MANAGE) {
            ManageScreen(
                state = state,
                onComplete = vm::setCompleted,
                onOpen = { id -> nav.navigate(Routes.flow(id)) },
                onRetry = vm::loadCatalog,
                onTab = nav::switchTab,
            )
        }
        composable(Routes.CHAT) { Chat(procedureId = null, onBack = null) }
        composable(Routes.PROCEDURE_CHAT) { entry ->
            Chat(procedureId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.FLOW) { entry ->
            FlowScreen(
                state = state,
                procedureId = entry.arguments?.getString("id").orEmpty(),
                onAnswer = vm::answer,
                onComplete = vm::setCompleted,
                onDismiss = vm::setDismissed,
                onConfirm = vm::setConfirmed,
                onRetry = vm::loadCatalog,
                onBack = { nav.popBackStack() },
                onAsk = { id -> nav.navigate(Routes.chat(id)) },
            )
        }
    }
}
