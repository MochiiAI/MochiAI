package com.mochi.agent.data.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val name: String? = null,
)

@Serializable
data class GitHubIssue(
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("pull_request") val pullRequest: GitHubPullRequestRef? = null,
)

@Serializable
data class GitHubPullRequestRef(
    val url: String? = null,
)

@Serializable
data class GitHubRepository(
    @SerialName("full_name") val fullName: String,
    @SerialName("default_branch") val defaultBranch: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val description: String? = null,
    val language: String? = null,
    val fork: Boolean? = null,
)

@Serializable
data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val state: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("head") val head: GitHubBranchRef? = null,
    @SerialName("base") val base: GitHubBranchRef? = null,
    val draft: Boolean? = null,
)

@Serializable
data class GitHubBranchRef(
    val label: String? = null,
    val ref: String? = null,
    val sha: String? = null,
)

@Serializable
data class CreatePullRequestRequest(
    val title: String,
    val head: String,
    val base: String = "main",
    val body: String? = null,
    val draft: Boolean = false,
)

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String = "",
    val labels: List<String> = emptyList(),
)

@Serializable
data class GitHubErrorEnvelope(
    val message: String,
    val documentation_url: String? = null,
)