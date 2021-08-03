package com.rsh.tkachenkoni.app_version_07_ble_04_example.models

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class WeightUnit {
    Unknown {
        override fun toString(): String {
            return "unknown"
        }
    },
    Kilograms {
        override fun toString(): String {
            return "Kg"
        }
    },
    Pounds {
        override fun toString(): String {
            return "lbs"
        }
    },
    Stones {
        override fun toString(): String {
            return "st"
        }
    }
}