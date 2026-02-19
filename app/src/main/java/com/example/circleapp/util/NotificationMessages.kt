package com.example.circleapp.util

object NotificationMessages {
    const val CHANNEL_ID = "circle_app_notifications"
    const val CHANNEL_NAME = "Circle App Notifications"
    const val CHANNEL_DESCRIPTION = "Notifications for circle updates, invites, and friend requests"

    fun getMemberJoinedTitle(circleName: String) = "New Member in $circleName"
    fun getMemberJoinedMessage(username: String) = "$username just joined the circle!"

    fun getMassUploadTitle() = "Massive Memories!"
    fun getMassUploadMessage(username: String) = "$username just uploaded over 20 photos in the last 10 minutes!"

    fun getCircleInviteTitle() = "New Circle Invite"
    fun getCircleInviteMessage(circleName: String) = "You've been invited to join $circleName"

    fun getFriendRequestTitle() = "New Friend Request"
    fun getFriendRequestMessage(username: String) = "$username sent you a friend request"

    fun getCircleClosingTitle(circleName: String) = "$circleName is Closing Soon"
    fun getCircleClosingMessage() = "This circle will close in 24 hours. Last chance to post!"

    fun getCircleDeletingTitle(circleName: String) = "$circleName is Deleting Soon"
    fun getCircleDeletingMessage() = "This circle will be deleted in 24 hours. Save any photos you want to keep!"
}
