package smoke

import stapel.result.ErrorEnvelope
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.classifyFailure
import stapel.result.map
import stapel.result.text.ErrorTextResolver
import stapel.result.text.LocaleTag

/** Uses the published artifact from common code (all four targets): a result, a failure, its text. */
fun smokeText(): String {
    val ok: StapelResult<Int, StapelError> = StapelResult.Ok(20).map { it + 1 }
    val failure = classifyFailure(ErrorEnvelope.synthesized(503))!!
    val text = ErrorTextResolver(emptyList()).describe(failure, LocaleTag("en"))
    return "${(ok as StapelResult.Ok).value} ${text.detail}"
}
