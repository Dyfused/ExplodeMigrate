package explode.migrate.legacy

enum class SetStatus {
	UNRANKED,
	RANKED,
	OFFICIAL,
	@Deprecated("use isReviewing instead")
	NEED_REVIEW,
	@Deprecated("use isHidden instead")
	HIDDEN;

}