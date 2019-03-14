package com.axelweinz.hockeyarlivefeed;

import com.google.firebase.database.Exclude;

public class TestClass {
    public String testString;
    public Double testDouble;

    @Exclude
    public Integer testInteger;
    public Integer testInteger2;

    public TestClass () {}

    public TestClass(String testString, Double testDouble) {
        this. testString = testString;
        this.testDouble = testDouble;
        this.testInteger = 10;
        this.testInteger2 = 12;
    }

    @Exclude
    public Integer getTestInteger() {
        return this.testInteger;
    }
    public Integer getTestInteger2() { return this.testInteger2;}

    public String getTestString() {
        return this.testString;
    }
}
