package com.example.poc.platform.agent.skill;

import com.example.poc.platform.agent.AgentPermissionMode;
import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
public class AgentSkillRegistry {
    private static final List<String> TRUSTED_SKILL_PATHS = List.of(
        "agent-skills/scenario-authoring/SKILL.md",
            "agent-skills/suite-authoring/SKILL.md",
            "agent-skills/scenario-analysis/SKILL.md",
            "agent-skills/openapi-analysis/SKILL.md",
            "agent-skills/secure-payload/SKILL.md",
            "agent-skills/failure-analysis/SKILL.md");

    private final Map<String, AgentSkill> skills;

    public AgentSkillRegistry() {
        Map<String, AgentSkill> loaded = new LinkedHashMap<>();
        for (String path : TRUSTED_SKILL_PATHS) {
            AgentSkill skill = load(path);
            if (loaded.put(skill.name(), skill) != null) {
                throw new IllegalStateException("Duplicate Agent Skill: " + skill.name());
            }
        }
        skills = Map.copyOf(loaded);
    }

    public List<AgentSkill> list() {
        return List.copyOf(skills.values());
    }

    public List<AgentSkill> select(AgentTaskRecord task, String userText, String scenarioText) {
        List<AgentSkill> selected = new ArrayList<>();
        if ("SCENARIO".equals(task.targetType())) {
            addEnabled(selected, task.permissionMode() == AgentPermissionMode.READ_ONLY
                    ? "scenario-analysis"
                    : "scenario-authoring");
        }
        if ("SUITE".equals(task.targetType())) {
            addEnabled(selected, "suite-authoring");
        }
        String userSearchable = userText.toLowerCase(Locale.ROOT);
        String searchable = (userText + "\n" + scenarioText).toLowerCase(Locale.ROOT);
        AgentSkill secure = skills.get("secure-payload");
        if (task.permissionMode() != AgentPermissionMode.READ_ONLY && secure != null && secure.enabled()
                && secure.matchTerms().stream().map(term -> term.toLowerCase(Locale.ROOT)).anyMatch(searchable::contains)) {
            selected.add(secure);
        }
        AgentSkill openapi = skills.get("openapi-analysis");
        if (openapi != null && openapi.enabled()
                && openapi.matchTerms().stream()
                        .map(term -> term.toLowerCase(Locale.ROOT))
                        .anyMatch(userSearchable::contains)) {
            selected.add(openapi);
        }
        return List.copyOf(selected);
    }

    public AgentSkill require(String name) {
        AgentSkill skill = skills.get(name);
        if (skill == null) throw new IllegalArgumentException("Unknown Agent Skill: " + name);
        if (!skill.enabled()) throw new IllegalArgumentException("Agent Skill is disabled: " + name);
        return skill;
    }

    private void addEnabled(List<AgentSkill> selected, String name) {
        AgentSkill skill = require(name);
        selected.add(skill);
    }

    private AgentSkill load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try {
            String markdown = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!markdown.startsWith("---\n")) throw invalid(path, "missing YAML front matter");
            int end = markdown.indexOf("\n---\n", 4);
            if (end < 0) throw invalid(path, "unterminated YAML front matter");
            String frontMatter = markdown.substring(4, end);
            String instructions = markdown.substring(end + 5).trim();
            ObjectMapper mapper = YAMLMapper.builder().build();
            Map<String, Object> metadata = mapper.readValue(frontMatter, new TypeReference<>() {});
            String name = requiredText(metadata, "name", path);
            if (List.of("scenario-authoring", "suite-authoring").contains(name)) {
                try (var shared = new ClassPathResource("agent-skills/scenario-dsl-rules.md").getInputStream()) {
                    instructions += "\n\n" + new String(shared.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            }
            return new AgentSkill(
                    name,
                    requiredText(metadata, "description", path),
                    requiredText(metadata, "version", path),
                    requiredText(metadata, "riskLevel", path),
                    stringList(metadata.get("requiredTools"), path, "requiredTools"),
                    stringList(metadata.get("matchTerms"), path, "matchTerms"),
                    Boolean.TRUE.equals(metadata.get("enabled")),
                    instructions,
                    "classpath:" + path);
        } catch (IOException | JacksonException exception) {
            throw invalid(path, exception.getMessage());
        }
    }

    private String requiredText(Map<String, Object> metadata, String field, String path) {
        Object value = metadata.get(field);
        if (!(value instanceof String text) || text.isBlank()) throw invalid(path, "missing " + field);
        return text.trim();
    }

    private List<String> stringList(Object value, String path, String field) {
        if (!(value instanceof List<?> list)) throw invalid(path, field + " must be a list");
        return list.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private IllegalStateException invalid(String path, String detail) {
        return new IllegalStateException("Invalid trusted Agent Skill " + path + ": " + detail);
    }
}
