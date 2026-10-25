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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.R
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.extension.getLocalIpFromDI
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

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = skipOnboarding) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
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
                    .padding(top = 14.dp, bottom = 10.dp)
            )

            Button(
                onClick = {
                    if (isLastPage) {
                        finishOnboarding()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_next),
                    fontWeight = FontWeight.Bold
                )
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
                .padding(top = 8.dp, bottom = 20.dp)
                .height(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.illustration,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
        Headline(stringResource(page.headline))
        Spacer(modifier = Modifier.height(10.dp))
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
        Spacer(modifier = Modifier.height(10.dp))
        BodyText(stringResource(R.string.onboarding_s3_body))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.onboarding_username_label)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(PAGE_COUNT) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .width(if (active) 16.dp else 6.dp)
                    .height(6.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = if (active) RoundedCornerShape(3.dp) else CircleShape
                    )
            )
        }
    }
}