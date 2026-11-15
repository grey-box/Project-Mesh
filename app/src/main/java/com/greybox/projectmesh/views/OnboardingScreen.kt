package com.greybox.projectmesh.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.R
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.extension.getLocalIpFromDI
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.rememberReduceMotion
import com.greybox.projectmesh.viewModel.OnboardingViewModel
import kotlinx.coroutines.launch
import org.kodein.di.compose.localDI
import org.kodein.di.instance

private const val PAGE_COUNT = 3

private data class InfoPage(
    val illustration: ImageVector,
    val headline: Int,
    val body: Int,
)

private val infoPages = listOf(
    InfoPage(
        illustration = Icons.Outlined.Hub,
        headline = R.string.onboarding_s1_title,
        body = R.string.onboarding_s1_body
    ),
    InfoPage(
        illustration = Icons.Outlined.WifiTethering,
        headline = R.string.onboarding_s2_title,
        body = R.string.onboarding_s2_body
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val di = localDI()
    val localIp = getLocalIpFromDI(di)
    val settingsRepository: SettingsRepository by di.instance()

    val factory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return OnboardingViewModel(
                        userRepository = GlobalApp.GlobalUserRepo.userRepository,
                        prefs = GlobalApp.GlobalUserRepo.prefs,
                        settingsRepository = settingsRepository,
                        localIp = localIp
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    val onboardingViewModel: OnboardingViewModel = viewModel(factory = factory)
    val uiState by onboardingViewModel.uiState.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1
    val reduceMotion = rememberReduceMotion()

    // Honours the system "Remove animations" setting. The page still changes; it just does
    // not slide there. A full-width horizontal pan is among the motions most likely to
    // provoke nausea or dizziness in someone with a vestibular disorder, and onboarding is
    // the one screen nobody can skip past. SC 2.3.3 (Animation from Interactions).
    val goToPage: suspend (Int) -> Unit = { page ->
        if (reduceMotion) pagerState.scrollToPage(page)
        else pagerState.animateScrollToPage(page)
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { goToPage(pagerState.currentPage - 1) }
    }

    val finishOnboarding: () -> Unit = {
        if (uiState.username.isNullOrBlank()) {
            onboardingViewModel.blankUsernameGenerator{ generatedName ->
                onboardingViewModel.onUsernameChange(generatedName)

                onboardingViewModel.handleFirstTimeSetup { onComplete() }
            }
        } else {
            onboardingViewModel.handleFirstTimeSetup { onComplete() }
        }
    }

    val skipOnboarding: () -> Unit = {
        onboardingViewModel.blankUsernameGenerator { generatedName ->
            onboardingViewModel.onUsernameChange(generatedName)
            onboardingViewModel.handleFirstTimeSetup { onComplete() }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = MeshTheme.spacing.md, vertical = MeshTheme.spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = skipOnboarding) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = MeshTheme.colors.onSurfaceVariant
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page < infoPages.size) {
                    InfoPageContent(infoPages[page])
                } else {
                    UsernamePageContent(
                        username = uiState.username,
                        onUsernameChange = { newValue -> onboardingViewModel.onUsernameChange(newValue) }
                    )
                }
            }

            PageIndicator(
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = MeshTheme.spacing.md, bottom = MeshTheme.spacing.sm)
            )

            Button(
                onClick = {
                    if (isLastPage) {
                        finishOnboarding()
                    } else {
                        scope.launch { goToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // heightIn, not height. A hard 48dp clipped the label at the 200% font
                    // scale Android permits — and this is the first screen anyone sees, so
                    // a user who runs large text met a button whose text was cut off
                    // before they had used the app at all. SC 1.4.4.
                    .heightIn(min = MeshTheme.size.buttonHeight)
            ) {
                Text(text = stringResource(R.string.onboarding_next))
            }
        }
    }
}

@Composable
private fun InfoPageContent(page: InfoPage) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MeshTheme.spacing.sm, bottom = MeshTheme.spacing.lg)
                .height(MeshTheme.size.illustrationPanel)
                .background(
                    color = MeshTheme.colors.primaryContainer,
                    shape = MeshTheme.shapes.large
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.illustration,
                contentDescription = null,
                tint = MeshTheme.colors.onPrimaryContainer,
                modifier = Modifier.size(MeshTheme.size.iconEmptyState)
            )
        }
        Headline(stringResource(page.headline))
        Spacer(modifier = Modifier.height(MeshTheme.spacing.sm))
        BodyText(stringResource(page.body))
    }
}

@Composable
private fun UsernamePageContent(
    username: String,
    onUsernameChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Headline(stringResource(R.string.onboarding_s3_title))
        Spacer(modifier = Modifier.height(MeshTheme.spacing.sm))
        BodyText(stringResource(R.string.onboarding_s3_body))
        Spacer(modifier = Modifier.height(MeshTheme.spacing.md))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.onboarding_username_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MeshTheme.typography.headlineSmall,
        color = MeshTheme.colors.onSurface,
        // headlineSmall made this look like a heading; it did not make it one. Declaring
        // it means TalkBack announces "heading" and can jump between onboarding pages by
        // heading rather than swiping through each paragraph. SC 1.3.1.
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MeshTheme.typography.bodyMedium,
        color = MeshTheme.colors.onSurfaceVariant
    )
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MeshTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(PAGE_COUNT) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .width(if (active) MeshTheme.size.pageIndicatorDotActive else MeshTheme.size.pageIndicatorDot)
                    .height(MeshTheme.size.pageIndicatorDot)
                    .background(
                        color = if (active) MeshTheme.colors.primary
                        else MeshTheme.colors.outlineVariant,
                        // A 3dp radius on a 6dp-tall box is just a stadium, which is what
                        // CircleShape already gives at any size, so both states use it.
                        shape = CircleShape
                    )
            )
        }
    }
}