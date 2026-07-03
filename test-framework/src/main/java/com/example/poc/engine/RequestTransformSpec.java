package com.example.poc.engine;

public class RequestTransformSpec {
    private String type;
    private String algorithm = "pgp";
    private String plaintextFormat = "json";
    private TransformOutputSpec output = new TransformOutputSpec();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public TransformOutputSpec getOutput() {
        return output;
    }

    public void setOutput(TransformOutputSpec output) {
        this.output = output == null ? new TransformOutputSpec() : output;
    }
}
