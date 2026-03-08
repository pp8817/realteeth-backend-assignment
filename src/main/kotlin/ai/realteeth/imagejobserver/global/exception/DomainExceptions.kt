package ai.realteeth.imagejobserver.global.exception

class ResourceNotFoundException(message: String) : RuntimeException(message)

class IllegalStateTransitionException(message: String) : RuntimeException(message)

class DataIntegrityException(message: String) : RuntimeException(message)
