package explode.migrate.legacy

data class UserPermission(
	/**
	 * Permissions to manage the Review things. Also has the permission to view the 'Review' category of the Store.
	 */
	var review: Boolean = false,

	/**
	 * Permissions to manage the database and to view the sensitive data.
	 */
	var operator: Boolean = false,
)