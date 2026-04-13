package com.andere.android.domain.usecase

import com.andere.android.domain.model.Post
import com.andere.android.domain.model.PostFilter

class FilterPostsUseCase {
    operator fun invoke(posts: List<Post>, filter: PostFilter): List<Post> = posts.filter(filter::matches)
}
