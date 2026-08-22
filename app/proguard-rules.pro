# Room and Hilt generate code that is only reached reflectively at runtime; their
# own consumer rules cover this, so nothing app-specific is needed yet.
# Native (JNI) entry points will need explicit keeps once :core:llm lands.
