package com.rsh.tkachenkoni.app_version_07_ble_04_example.models

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class TemperatureType(value: Int) {
    Armpit(1), Body(2), Ear(3), Finger(4), GastroIntestinalTract(5), Mouth(6), Rectum(7), Toe(8), Tympanum(
        9
    );

    private val value: Int
    fun getValue(): Int {
        return value
    }

    companion object {
        fun fromValue(value: Int): TemperatureType? {
            for (type in values()) {
                if (type.getValue() == value) return type
            }
            return null
        }
    }

    init {
        this.value = value
    }
}