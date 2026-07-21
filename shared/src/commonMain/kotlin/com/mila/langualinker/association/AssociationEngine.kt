package com.mila.langualinker.association

interface AssociationEngine {
    fun generateAssociations(input: String): List<String>
}
