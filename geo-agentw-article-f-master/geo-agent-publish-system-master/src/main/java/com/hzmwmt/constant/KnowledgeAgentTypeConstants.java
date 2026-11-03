package com.hzmwmt.constant;

public final class KnowledgeAgentTypeConstants {

    private KnowledgeAgentTypeConstants() {
    }

    public static final int HOSPITAL = 1;

    public static final int DEPARTMENT = 2;

    public static final int DOCTOR = 3;

    public static boolean isValid(Integer agentType) {
        return agentType != null
                && (agentType == HOSPITAL || agentType == DEPARTMENT || agentType == DOCTOR);
    }
}
