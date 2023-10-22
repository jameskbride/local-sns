package com.jameskbride.fakesns.models

import java.io.Serializable

data class Topic(val arn:String, val name:String):Serializable {
    companion object {
        val namePattern = """([\w+_-]{1,256})"""
        val arnPattern = """([\w+_:-]{1,512})"""
    }
}
