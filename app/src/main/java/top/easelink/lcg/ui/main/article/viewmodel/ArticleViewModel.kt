package top.easelink.lcg.ui.main.article.viewmodel

import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jsoup.HttpStatusException
import timber.log.Timber
import top.easelink.lcg.R
import top.easelink.lcg.mta.EVENT_ADD_TO_FAVORITE
import top.easelink.lcg.mta.sendEvent
import top.easelink.lcg.ui.main.articles.viewmodel.ArticleFetcher.Companion.FETCH_INIT
import top.easelink.lcg.ui.main.articles.viewmodel.ArticleFetcher.Companion.FETCH_MORE
import top.easelink.lcg.ui.main.model.BlockException
import top.easelink.lcg.ui.main.source.local.ArticlesLocalDataSource
import top.easelink.lcg.ui.main.source.local.SP_KEY_SYNC_FAVORITE
import top.easelink.lcg.ui.main.source.local.SharedPreferencesHelper
import top.easelink.lcg.ui.main.source.model.ArticleAbstractResponse
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.model.Post
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource
import top.easelink.lcg.utils.RegexUtils
import top.easelink.lcg.utils.showMessage
import java.util.*

class ArticleViewModel: ViewModel(), ArticleAdapterListener {
    val posts = MutableLiveData<MutableList<Post>>()
    val isBlocked = MutableLiveData<Boolean>()
    val isNotFound = MutableLiveData<Boolean>()
    val shouldDisplayPosts = MutableLiveData<Boolean>()
    val articleTitle = MutableLiveData<String>()
    val isLoading = MutableLiveData<Boolean>()

    private var mUrl: String? = null
    private var nextPageUrl: String? = null
    // formhash is used for add favorite/reply/rate etc
    private var mFormHash: String? = null
    private var articleAbstract: ArticleAbstractResponse? = null

    fun setUrl(url: String) {
        mUrl = url
    }

    override fun fetchArticlePost(type: Int) {
        isLoading.value = true
        val query: String? =
            when (type) {
                FETCH_INIT -> mUrl
                FETCH_MORE -> nextPageUrl
                else -> null
            }

        if (query.isNullOrBlank()) {
            isLoading.value = false
            return
        }
        GlobalScope.launch(Dispatchers.IO) {
            try {
                ArticlesRemoteDataSource.getArticleDetail(query)?.let {
                    articleAbstract = it.articleAbstractResponse
                    if (it.articleTitle.isBlank()) {
                        articleTitle.postValue(it.articleTitle)
                    }
                    if (it.postList.isNotEmpty()) {
                        if (type == FETCH_INIT) {
                            posts.postValue(it.postList.toMutableList())
                        } else {
                            val list = posts.value
                            if (list != null && list.isNotEmpty()) {
                                list.addAll(it.postList)
                                posts.postValue(list)
                            } else {
                                posts.postValue(it.postList.toMutableList())
                            }
                        }
                    }
                    mFormHash = it.fromHash
                    shouldDisplayPosts.postValue(true)
                }
            } catch (e: Exception) {
                when(e) {
                    is BlockException -> setArticleBlocked()
                    is HttpStatusException -> setArticleNotFound()
                }
                Timber.e(e)
            }
            isLoading.postValue(false)
        }
    }

    override fun replyAdd(url: String) {
        if (TextUtils.isEmpty(url)) {
            isLoading.value = false
            throw IllegalStateException()
        }
        GlobalScope.launch(Dispatchers.IO) {
            ArticlesRemoteDataSource.replyAdd(url).also {
                showMessage(it)
            }
        }
    }

    fun extractDownloadUrl(): ArrayList<String>? {
        val patternLanzous = "https://www.lanzous.com/[a-zA-Z0-9]{4,10}"
        val patternBaidu = "https://pan.baidu.com/s/.{23}"
        val patternT = "http://t.cn/[a-zA-Z0-9]{8}"
        val list: List<Post>? = posts.value
        var resSet: HashSet<String>? = null
        if (list != null && !list.isEmpty()) {
            val content = list[0].content
            resSet = RegexUtils.extractInfoFrom(content, patternLanzous)
            resSet.addAll(RegexUtils.extractInfoFrom(content, patternBaidu))
            resSet.addAll(RegexUtils.extractInfoFrom(content, patternT))
        }
        return resSet?.let {
            ArrayList(it)
        }
    }

    fun addToFavorite() {
        sendEvent(EVENT_ADD_TO_FAVORITE)
        val posts: MutableList<Post> = posts.value ?: mutableListOf()
        if (posts.isEmpty()) {
            showMessage(R.string.add_to_favorite_failed)
        }
        GlobalScope.launch(Dispatchers.IO) {
            // if title is null, use abstract's title, this rarely happens
            val title = articleTitle.value?:articleAbstract?.title
            val threadId = extractThreadId(mUrl)
            val author = posts[0].author
            val content = articleAbstract?.description ?: ""
            val articleEntity = ArticleEntity(
                title ?: "未知标题",
                author,
                mUrl!!,
                content,
                System.currentTimeMillis()
            )
            val syncFavoritesEnable =
                SharedPreferencesHelper.getUserSp().getBoolean(SP_KEY_SYNC_FAVORITE, false)
            if (syncFavoritesEnable) {
                if (threadId != null && mFormHash != null) {
                    ArticlesRemoteDataSource.addFavorites(threadId, mFormHash!!).let {
                        if (it)
                            showMessage(R.string.sync_favorite_successfully)
                        else
                            showMessage(R.string.sync_favorite_failed)
                    }
                }
            }
            try {
                val res = ArticlesLocalDataSource.addArticleToFavorite(articleEntity)
                // don't show local message while sync to network
                if (!syncFavoritesEnable) {
                    if (res) {
                        showMessage(R.string.add_to_favorite_successfully)
                    } else {
                        showMessage(R.string.add_to_favorite_failed)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
                showMessage(R.string.add_to_favorite_failed)
            }
        }
    }

    private fun setArticleNotFound() {
        isNotFound.value = true
        shouldDisplayPosts.value = false
    }

    private fun setArticleBlocked() {
        isBlocked.value = true
        shouldDisplayPosts.value = false
    }

    private fun extractThreadId(url: String?): String? {
        return if (TextUtils.isEmpty(url)) {
            null
        } else try {
            url!!.split("-").toTypedArray()[1]
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }
}