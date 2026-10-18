package com.swpp.team5.frameless.data

import android.util.Log
import com.swpp.team5.frameless.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** 서버 요청과 응답 파싱만 담당한다. 화면 코드는 여기에 두지 않는다. */
internal object AnalysisApi {
    // Gradle supplies the shared team backend URL or a developer's local override.
    private val BASE_URL = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    private const val MAX_RELATED_ARTICLES = 3
    private const val LOG_TAG = "FrameLESS.Analysis"

    private val EXPRESSION_CODES = setOf("A", "B", "C", "D", "E", "F", "G")
    private val INFORMATION_CODES = setOf("I1", "I2", "I3", "I4", "I5")
    private val BASELINE_SOURCES = setOf("outlet", "")
    private val POLARITIES = setOf("negative", "positive", "none")
    private val AIMS = setOf("direct", "indirect")
    private val ABSENCE_REASONS = setOf("timing_impossible", "format", "editorial", "unknown")
    private val RELEVANCES = setOf("issue_relevant", "peripheral")

    suspend fun analyze(articleUrl: String): AnalysisResult = withContext(Dispatchers.IO) {
        val connection = (URL("$BASE_URL/api/analyze").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = 10_000
            connection.readTimeout = 600_000
            connection.doOutput = true
            val request = JSONObject().put("url", articleUrl)
                .put("max_related", MAX_RELATED_ARTICLES)
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val analysisId = connection.getHeaderField("X-Analysis-ID").orEmpty()
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw IllegalStateException("서버 응답을 읽지 못했어요. (HTTP $status)")
            }
            if (status !in 200..299) {
                val error = json.optJSONObject("error")
                Log.w(LOG_TAG, "Backend status=$status analysis_id=$analysisId error_code=${error?.optString("code")}")
                val message = error?.optString("message")?.takeIf { it.isNotBlank() }
                val code = error?.optString("code")?.takeIf { it.isNotBlank() }
                // 서버 메시지인지 앱 메시지인지 구분되도록 상태 코드와 분석 ID를 붙인다.
                val detail = listOfNotNull("HTTP $status", code, analysisId.takeIf { it.isNotBlank() }?.let { "ID $it" })
                    .joinToString(" · ")
                throw IllegalStateException(
                    (message?.let { "서버: $it" } ?: "분석 요청이 실패했어요.") + "\n($detail)"
                )
            }
            Log.i(LOG_TAG, "Backend status=$status analysis_id=$analysisId")
            if (BuildConfig.DEBUG) logResponseShape(json)
            parseAnalysis(json, articleUrl)
        } catch (_: ConnectException) {
            throw IllegalStateException("서버에 연결할 수 없어요. 서버 주소와 상태를 확인해줘.")
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException("분석 시간이 초과됐어요. 잠시 후 다시 시도해줘.")
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseAnalysis(root: JSONObject, requestedUrl: String): AnalysisResult {
        val source = root.optJSONObject("source_analysis")
            ?: throw IllegalStateException("분석 결과에 입력 기사 정보가 없어요.")
        val comparison = root.optJSONObject("comparison")
            ?: throw IllegalStateException("분석 결과에 비교 정보가 없어요.")
        val sourceUrl = source.optString("url").ifBlank { requestedUrl }
        val sourceTitle = source.optString("title").ifBlank { sourceUrl }

        // 입력 기사 클레임
        val sourceClaimsJson = source.optJSONArray("claims") ?: JSONArray()
        val sourceClaims = (0 until sourceClaimsJson.length()).mapNotNull { i ->
            val c = sourceClaimsJson.optJSONObject(i) ?: return@mapNotNull null
            SourceClaim(
                index = i,
                text = c.optString("claim"),
                quote = c.optJSONObject("evidence")?.optString("quote").orEmpty()
            )
        }

        // 쟁점과 구성원 (서버 값 그대로)
        val issues = (comparison.optJSONArray("issues") ?: JSONArray()).objects().map { issue ->
            IssueGroup(
                label = issue.optString("issue"),
                members = (issue.optJSONArray("members") ?: JSONArray()).objects().map { m ->
                    IssueMember(
                        articleIndex = m.optInt("article_index", -1),
                        title = m.optString("title"),
                        url = m.optString("url").takeIf { it.isHttpUrl() },
                        sourceClaimIndex = if (m.has("source_claim_index") && !m.isNull("source_claim_index"))
                            m.optInt("source_claim_index", -1).takeIf { it >= 0 } else null,
                        relationRaw = m.optString("relation_to_source"),
                        quote = m.optJSONObject("evidence")?.optString("quote").orEmpty(),
                        relationOwnerRaw = m.optString("relation_owner")
                            .takeIf { m.has("relation_owner") && !m.isNull("relation_owner") && it.isNotBlank() },
                        speaker = m.optString("speaker").takeUnless { m.isNull("speaker") }.orEmpty(),
                        speechKindRaw = m.optString("speech_kind")
                            .takeIf { m.has("speech_kind") && !m.isNull("speech_kind") && it.isNotBlank() },
                        hasMultipleVoices = m.optBoolean("has_multiple_voices", false),
                        relationWarning = m.optString("relation_warning")
                            .takeIf { m.has("relation_warning") && !m.isNull("relation_warning") && it.isNotBlank() }
                    )
                },
                claimId = issue.optString("claim_id").ifBlank { null }
            )
        }
        val allMembers = issues.flatMap { it.members }

        val relatedJson = root.optJSONArray("related_articles") ?: JSONArray()
        // AI-generated adapter: explicit article_index keeps rubric-only baseline links available.
        // v1 responses lack this field, so their member-derived article list still works.
        val membersByIndex = allMembers.filter { it.articleIndex > 0 }.groupBy { it.articleIndex }
        val relatedByIndex = relatedJson.objects()
            .filter { it.has("article_index") && !it.isNull("article_index") }
            .groupBy { it.optInt("article_index", -1) }
            .filterKeys { it > 0 }
        val articles = buildList {
            add(ArticleRef(0, sourceTitle, sourceUrl))
            (membersByIndex.keys + relatedByIndex.keys).toSortedSet().forEach { index ->
                val ms = membersByIndex[index].orEmpty()
                val rs = relatedByIndex[index].orEmpty()
                val memberUrl = ms.firstOrNull { it.url != null }?.url
                val relatedUrl = rs.firstOrNull { it.optString("url").isHttpUrl() }
                    ?.optString("url")
                if (memberUrl != null && relatedUrl != null && memberUrl != relatedUrl) {
                    throw IllegalStateException("비교 기사 링크와 판정 결과의 기사 ID가 일치하지 않아요.")
                }
                add(ArticleRef(
                    articleIndex = index,
                    title = rs.firstOrNull { it.optString("title").isNotBlank() }?.optString("title")
                        ?: ms.firstOrNull { it.title.isNotBlank() }?.title ?: "다른 출처 $index",
                    url = relatedUrl ?: memberUrl
                ))
            }
        }

        // 선택된 issue member에 표시되지 않았다는 뜻만 남긴다. 누락 판정은 아니다.
        val memberUrls = allMembers.mapNotNull { it.url }.toSet() + sourceUrl
        val relatedWithoutMatches = relatedJson.objects().mapNotNull { r ->
            val url = r.optString("url")
            if (url.isNotBlank() && url !in memberUrls) r.optString("title").ifBlank { url } else null
        }

        // 기존 화면용 카드 (기존 로직 유지, relation 값만 보존)
        val claims = buildLegacyClaims(issues, sourceClaims, sourceTitle, sourceUrl)

        // 다른 기사에서 대응 문장을 찾지 못한 입력 기사 클레임
        val matchedSourceIndexes = allMembers
            .filter { it.articleIndex > 0 && it.quote.isNotBlank() }
            .mapNotNull { it.sourceClaimIndex }
            .toSet()
        val sourceOnlyClaims = sourceClaims.filter { it.index !in matchedSourceIndexes && it.quote.isNotBlank() }

        // 입력 기사 클레임과 연결되지 않은 다른 기사 문장
        val relatedOnlyMembers = allMembers.filter { m ->
            m.articleIndex > 0 && m.quote.isNotBlank() &&
                (m.sourceClaimIndex == null || sourceClaims.none { it.index == m.sourceClaimIndex })
        }

        return AnalysisResult(
            sourceTitle = sourceTitle,
            sourceUrl = sourceUrl,
            coreEvent = source.optString("core_event"),
            relatedArticleCount = relatedJson.length(),
            claims = claims,
            articles = articles,
            relatedWithoutMatches = relatedWithoutMatches,
            sourceClaims = sourceClaims,
            issues = issues,
            sourceOnlyClaims = sourceOnlyClaims,
            relatedOnlyMembers = relatedOnlyMembers,
            rubric = root.optJSONObject("rubric")?.let { parseRubric(it) },
            analysisStatus = root.optJSONObject("analysis_status")?.let { status ->
                AnalysisStatus(
                    executionStatus = status.optString("execution_status"),
                    comparisonStatus = status.optString("comparison_status"),
                    warnings = (status.optJSONArray("warnings") ?: JSONArray()).strings()
                )
            }
        )
    }

    private fun buildLegacyClaims(
        issues: List<IssueGroup>,
        sourceClaims: List<SourceClaim>,
        sourceTitle: String,
        sourceUrl: String
    ): List<ClaimPreview> {
        val claims = mutableListOf<ClaimPreview>()
        for (issue in issues) {
            val related = issue.members.filter { it.articleIndex > 0 }
            for ((sourceClaimIndex, matchesForClaim) in related.groupBy { it.sourceClaimIndex }) {
                val sourceClaim = sourceClaims.firstOrNull { it.index == sourceClaimIndex } ?: continue
                if (sourceClaim.quote.isBlank()) continue
                val sourcePassage = SourcePassage(sourceTitle, sourceClaim.quote, sourceUrl)
                val title = issue.label.ifBlank { sourceClaim.text }
                if (title.isBlank()) continue

                val same = matchesForClaim.filter { it.relation == Relation.SAME }
                val different = matchesForClaim.filter {
                    it.relation == Relation.OPPOSES || it.relation == Relation.DIFFERENT_INTERPRETATION
                }
                for ((type, matches) in listOf(ClaimType.SHARED to same, ClaimType.DIFFERENT to different)) {
                    val passages = matches.mapNotNull { member ->
                        if (member.quote.isBlank()) null else SourcePassage(
                            outlet = member.title.ifBlank { "다른 출처" },
                            excerpt = member.quote,
                            url = member.url,
                            relationRaw = member.relationRaw
                        )
                    }.distinctBy { it.url to it.excerpt }
                    if (passages.isEmpty()) continue
                    claims += ClaimPreview(
                        type = type,
                        summary = title,
                        explanation = if (type == ClaimType.SHARED) {
                            "입력 기사와 다른 출처에서 같은 주장에 대응하는 문장을 찾았어요."
                        } else {
                            "같은 주장에 대해 서버가 '반대되는 내용' 또는 '다른 해석'으로 분류한 문장을 찾았어요."
                        },
                        passages = listOf(sourcePassage) + passages
                    )
                }
            }
        }
        return claims
    }

    /* ---------------- rubric (v0.2.1) 파싱: 형식이 맞지 않으면 버린다 ---------------- */

    private fun parseRubric(r: JSONObject): RubricResult {
        var dropped = 0

        val questions = (r.optJSONArray("questions") ?: JSONArray()).objects().mapNotNull { q ->
            val id = q.optString("id")
            val text = q.optString("question")
            if (id.isBlank() || text.isBlank()) { dropped++; return@mapNotNull null }
            val answers = (q.optJSONArray("answers") ?: JSONArray()).objects().mapNotNull { a ->
                val status = a.optString("status")
                if (!a.has("article_index") || status !in setOf("answered", "absent")) { dropped++; return@mapNotNull null }
                QuestionAnswer(
                    articleIndex = a.optInt("article_index"),
                    answered = status == "answered",
                    summary = a.optString("summary"),
                    evidenceQuotes = (a.optJSONArray("evidence_quotes") ?: JSONArray()).strings(),
                    absenceReason = a.optString("absence_reason").takeIf { it in ABSENCE_REASONS },
                    relevance = a.optString("relevance").takeIf { it in RELEVANCES }
                )
            }
            RubricQuestion(id, text, answers)
        }

        val expressions = (r.optJSONArray("expression_differences") ?: JSONArray()).objects().mapNotNull { e ->
            val sentence = e.optString("sentence")
            val span = e.optString("span")
            val categories = (e.optJSONArray("categories") ?: JSONArray()).objects().mapNotNull { c ->
                val code = c.optString("code")
                val markedness = if (c.has("markedness") && !c.isNull("markedness"))
                    c.optInt("markedness", -1).takeIf { it in 0..2 } else null
                if (code in EXPRESSION_CODES && c.has("markedness") &&
                    (c.isNull("markedness") || markedness != null)) CategoryMark(code, markedness) else null
            }
            val baseline = e.optJSONObject("baseline")
            val baselineSource = baseline?.optString("source").orEmpty()
            val polarity = e.optString("polarity")
            val status = e.optString("status").ifBlank { "unspecified" }
            // 원문 추적 원칙: 표현은 반드시 같은 문장 안에 그대로 있어야 한다.
            val valid = e.has("article_index") && sentence.isNotBlank() && span.isNotBlank() &&
                sentence.contains(span) && categories.isNotEmpty() &&
                baselineSource in BASELINE_SOURCES && polarity in POLARITIES
            if (!valid) { dropped++; return@mapNotNull null }
            ExpressionDifference(
                articleIndex = e.optInt("article_index"),
                questionId = e.optionalString("question_id"),
                claimId = e.optionalString("claim_id"),
                sentence = sentence,
                span = span,
                categories = categories,
                baselineText = baseline?.optionalString("text").orEmpty(),
                baselineSource = baselineSource,
                baselineArticleIndex = baseline?.takeIf { it.has("article_index") && !it.isNull("article_index") }
                    ?.optInt("article_index"),
                target = e.optionalString("target"),
                polarity = polarity,
                aim = e.optString("aim").takeIf { it in AIMS },
                status = status,
                held = status != "provisional" || categories.any { it.markedness == null },
                note = e.optionalString("note").orEmpty()
            )
        }

        val informations = (r.optJSONArray("information_differences") ?: JSONArray()).objects().mapNotNull { i ->
            val code = i.optString("code")
            val value = i.optString("value")
            if (code !in INFORMATION_CODES || value.isBlank() || !i.has("article_index")) { dropped++; return@mapNotNull null }
            InformationDifference(
                code = code,
                articleIndex = i.optInt("article_index"),
                questionId = i.optionalString("question_id"),
                claimId = i.optionalString("claim_id"),
                sentence = i.optionalString("sentence"),
                value = value,
                target = i.optionalString("target"),
                polarity = i.optString("polarity").takeIf { it in POLARITIES }
            )
        }

        if (dropped > 0) Log.w(LOG_TAG, "rubric: dropped $dropped malformed item(s)")
        return RubricResult(
            version = r.optString("version"),
            judgmentStatus = r.optString("judgment_status"),
            questions = questions,
            expressions = expressions,
            informations = informations,
            droppedCount = dropped
        )
    }

    /** 디버그 빌드에서만: 서버가 실제로 보내는 키 목록을 남겨 파서가 놓치는 필드를 찾는다. */
    private fun logResponseShape(root: JSONObject) {
        fun keys(o: JSONObject?) = o?.keys()?.asSequence()?.toList()?.sorted().orEmpty()
        val source = root.optJSONObject("source_analysis")
        val comparison = root.optJSONObject("comparison")
        val firstIssue = comparison?.optJSONArray("issues")?.optJSONObject(0)
        Log.d(LOG_TAG, "keys root=${keys(root)}")
        Log.d(LOG_TAG, "keys source_analysis=${keys(source)}")
        Log.d(LOG_TAG, "keys source_analysis.claims[0]=${keys(source?.optJSONArray("claims")?.optJSONObject(0))}")
        Log.d(LOG_TAG, "keys comparison=${keys(comparison)}")
        Log.d(LOG_TAG, "keys comparison.issues[0]=${keys(firstIssue)}")
        Log.d(LOG_TAG, "keys comparison.issues[0].members[0]=${keys(firstIssue?.optJSONArray("members")?.optJSONObject(0))}")
        Log.d(LOG_TAG, "keys related_articles[0]=${keys(root.optJSONArray("related_articles")?.optJSONObject(0))}")
        val relations = comparison?.optJSONArray("issues")?.objects().orEmpty()
            .flatMap { (it.optJSONArray("members") ?: JSONArray()).objects() }
            .map { it.optString("relation_to_source") }.distinct()
        Log.d(LOG_TAG, "relation_to_source values=$relations")
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    /** org.json can stringify JSON null as the literal "null"; keep optional fields truly absent. */
    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    private fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")
}
