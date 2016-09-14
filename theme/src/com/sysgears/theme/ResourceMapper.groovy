package com.sysgears.theme

import com.sysgears.grain.taglib.Site
import com.sysgears.theme.pagination.Paginator

/**
 * Change pages urls and extend models.
 */
class ResourceMapper {

    /**
     * Site reference, provides access to site configuration.
     */
    private final Site site

    public ResourceMapper(Site site) {
        this.site = site
    }

    /**
     * This closure is used to transform page URLs and page data models.
     */
    def map = { resources ->

        def refinedResources = resources.findResults(filterPublished).collect { Map resource ->
            customizeUrls << fillDates << fillRelatedPosts << resource
        }.sort { -it.date.time }

        customizeModels << refinedResources
    }

    /**
     * Customizes pages models, applies pagination (creates new pages)
     */
    private def customizeModels = { List resources ->
        def posts = resources.findAll { it.layout == 'post' }
        Set<String> tags = posts.inject([]) { List tags, Map post -> tags + post.categories }

        def postsByCategory = { tag -> posts.findAll { post -> tag in post.categories } }

        def postsByAuthor = posts.groupBy { it.author }

        resources.inject([]) { List updatedResources, Map page ->
            def applyPagination = { items, perPage, url, model = [:] ->
                updatedResources += Paginator.paginate(items, 'posts', perPage, url, page + model)
            }
            switch (page.url) {
                case '/':
                    applyPagination(posts, site.posts_per_blog_page, page.url)
                    break
                case '/archives/':
                    applyPagination(posts, site.posts_per_archive_page, page.url)
                    break
                case '/authors/':
                    postsByAuthor.each { String author, List items ->
                        if (author) {
                            applyPagination(items, site.posts_per_blog_page, "${page.url}${author.encodeAsSlug()}/", [author: author])
                        }
                    }
                    break
                case '/categories/':
                    tags.each { String tag ->
                        applyPagination(postsByCategory(tag), site.posts_per_blog_page, "${page.url}${tag.encodeAsSlug()}/", [tag: tag])
                    }
                    break
                case '/atom.xml':
                    int maxRss = site.rss.post_count
                    def lastUpdated = new Date(posts.max { it.updated.time }.updated.time as Long)

                    // default feed
                    updatedResources << (page + [posts: posts.take(maxRss), lastUpdated: lastUpdated])

                    // feed for each category
                    updatedResources += tags.collect { String tag ->
                        def feedUrl = "/categories/${tag.encodeAsSlug()}/atom.xml"
                        page + [url: feedUrl, tag: tag, posts: postsByCategory(tag).take(maxRss)]
                    }
                    break
                case ~/${site.posts_base_url}.*/:
                    def post = posts.find { it.url == page.url }
                    def index = posts.indexOf(post)
                    def prev = index > 0 ? posts[index - 1] : null
                    def next = posts[index + 1]
                    updatedResources << (page + [prev_post: prev, next_post: next])
                    break
                default:
                    updatedResources << page
            }

            updatedResources
        }
    }
    /**
     * Customize site post URLs
     */
    private def customizeUrls = { Map resource ->
        String location = resource.location
        def update = [:]

        switch (location) {
            case ~/\/posts\/.*/:
                update.url = getPostUrl(site.posts_base_url, location)
                break
        }

        resource + update
    }

    /**
     * Fills in page 'related' field which may contain related posts.
     *
     * Related posts here are pages under /posts/ location which have at least one
     * common entry in the "categories" list property.
     */
    private def fillRelatedPosts = { Map it ->
        isPost(it) ?
                it + [related: getPosts().grep { post -> !post.categories.disjoint(it.categories) }] :
                it
    }

    /**
     * Checks whether provided resource is a blog post page or not.
     */
    private def isPost = { Map it ->
        it.location.startsWith("/posts/")
    }

    /**
     * Retrieves all the blog post pages of the website.
     *
     * @return website blog posts collection
     */
    private List<Map> getPosts() {
        site.pages.findAll isPost
    }
    /**
     * Creates url for page. Cuts date and extension from the file name '2013-01-01-file-name.markdown'.
     *
     * @param basePath base path to the page
     * @param location location of the file
     *
     * @return formatted url to the page.
     */
    private static String getPostUrl(String basePath, String location) {
        basePath + location.substring(location.lastIndexOf('/') + 12, location.lastIndexOf('.')) + '/'
    }

    /**
     * Excludes resources with published property set to false,
     * unless it is allowed to show unpublished resources in SiteConfig.
     */
    private def filterPublished = { Map it ->
        (it.published != false || site.show_unpublished) ? it : null
    }

    /**
     * Fills in page `date` and `updated` fields 
     */
    private def fillDates = { Map it ->
        def update = [date   : it.date ? Date.parse(site.datetime_format, it.date) : new Date(it.dateCreated as Long),
                      updated: it.updated ? Date.parse(site.datetime_format, it.updated) : new Date(it.lastUpdated as Long)]
        it + update
    }
}
