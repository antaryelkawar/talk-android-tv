/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.contacts.ContactsActivity
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.settings.SettingsActivity
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class TvConversationListActivity : ComponentActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderOld

    private lateinit var viewModel: ConversationsListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[ConversationsListViewModel::class.java]

        val currentUser = currentUserProvider.currentUser.blockingGet()
        val baseUrl = currentUser?.baseUrl ?: ""

        setContent {
            MaterialTheme {
                TvConversationListScreen(
                    conversationsFlow = viewModel.getRoomsStateFlow,
                    baseUrl = baseUrl,
                    onConversationClick = { conversation -> openConversation(conversation) },
                    onSettingsClick = { openSettings() },
                    onNewConversationClick = { openNewConversation() }
                )
            }
        }

        viewModel.getRooms(currentUser!!)
    }

    override fun onResume() {
        super.onResume()
        val currentUser = currentUserProvider.currentUser.blockingGet()
        if (currentUser != null) {
            viewModel.getRooms(currentUser)
        }
    }

    private fun openConversation(conversation: ConversationModel) {
        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, conversation.token)
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openNewConversation() {
        val intent = Intent(this, ContactsActivity::class.java)
        startActivity(intent)
    }
}
