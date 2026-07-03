package com.example.poc.engine;

public class DecryptSpec {
    private String name;
    private DecryptInputSpec input = new DecryptInputSpec();
    private String algorithm = "fake";
    private String plaintextFormat = "json";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DecryptInputSpec getInput() {
        return input;
    }

    public void setInput(DecryptInputSpec input) {
        this.input = input == null ? new DecryptInputSpec() : input;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getPlaintextFormat() {
        return plaintextFormat;
    }

    public void setPlaintextFormat(String plaintextFormat) {
        this.plaintextFormat = plaintextFormat;
    }
}
