package com.farcast.agent.darkalgorithm.features

/**
 * #p-sqli. The website's own author already made this call and explained it
 * in-panel — preserved verbatim rather than silently dropped or silently
 * built for real. Porting "every feature" doesn't mean overriding a
 * deliberate prior refusal; it means keeping this screen's text and
 * disabled input exactly as authored, on every platform.
 *
 * Real sqlmap: fingerprints the DB engine, then automates error-based,
 * boolean-blind, time-blind, and UNION-based injection to extract data.
 * That's active exploitation of a live target — a different category from
 * every passive lookup elsewhere in FARCAST, and not something this app
 * wires up, authorization checkbox or not.
 */
object SqliTestingNotice {
    const val WARNING = "Only ever point this at a site you own or have explicit written " +
        "authorization to test. Running injection tests against a site without permission is " +
        "illegal in most places — unauthorized access / computer misuse statutes."

    const val EXPLANATION = "I'm not wiring up working injection logic here, even gated behind " +
        "an authorization checkbox — that crosses from \"recon tool\" into \"exploit tool\" " +
        "regardless of the confirmation step."

    const val ALTERNATIVES = "For authorized pentest work: run real sqlmap locally " +
        "(github.com/sqlmapproject/sqlmap, free, that's what it's for), or self-host OWASP ZAP " +
        "(zaproxy.org) and call its API from your own infrastructure."

    /** The UI should render WARNING/EXPLANATION/ALTERNATIVES as static text
     *  and the target-URL field permanently disabled — same as the website's
     *  `<input id="sqliin" ... disabled>`. */
}
