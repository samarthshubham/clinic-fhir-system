package io.github.samarthshubham.payer.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

@Service
public class CoverageRuleService {

  private static final Logger log = LoggerFactory.getLogger(CoverageRuleService.class);

  private static final ObjectMapper RULES_MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final Map<String, CoverageRule> rulesByCode;

  public CoverageRuleService(
      @Value("${payer.coverage-rules:classpath:coverage-rules.yaml}") Resource rulesFile) {
    List<CoverageRule> rules = load(rulesFile);
    this.rulesByCode = index(rules, rulesFile.getDescription());
    log.info("Loaded {} coverage rules from {}", rules.size(), rulesFile.getDescription());
  }

  public CoverageRule findRule(String system, String code) {
    CoverageRule rule = rulesByCode.get(key(system, code));
    return rule != null ? rule : CoverageRule.noPriorAuthNeeded(system, code);
  }

  private static List<CoverageRule> load(Resource rulesFile) {
    CoverageRules file;
    try (InputStream input = rulesFile.getInputStream()) {
      Object yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
      file = RULES_MAPPER.convertValue(yaml, CoverageRules.class);
    } catch (IllegalArgumentException e) {
      throw invalid(rulesFile.getDescription(), describe(e));
    } catch (IOException | YAMLException e) {
      throw invalid(rulesFile.getDescription(), e.getMessage());
    }
    if (file == null || file.rules().isEmpty()) {
      throw invalid(rulesFile.getDescription(), "the file has no rules");
    }
    return file.rules();
  }

  private static String describe(IllegalArgumentException e) {
    if (!(e.getCause() instanceof JsonMappingException mapping)) {
      return e.getMessage();
    }
    StringBuilder path = new StringBuilder();
    for (JsonMappingException.Reference reference : mapping.getPath()) {
      if (reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex() + 1).append(']');
      } else if (reference.getFieldName() != null) {
        path.append(path.isEmpty() ? "" : ".").append(reference.getFieldName());
      }
    }
    return path + ": " + mapping.getOriginalMessage();
  }

  private static Map<String, CoverageRule> index(List<CoverageRule> rules, String source) {
    Map<String, CoverageRule> byCode = new HashMap<>();
    for (int i = 0; i < rules.size(); i++) {
      CoverageRule rule = rules.get(i);
      if (rule == null) {
        throw invalid(source, "rule " + (i + 1) + " is empty");
      }
      String label = "rule " + (i + 1) + " (" + rule.system() + "|" + rule.code() + ")";
      List<String> problems = problemsWith(rule);
      if (!problems.isEmpty()) {
        throw invalid(source, label + ": " + String.join("; ", problems));
      }
      if (byCode.putIfAbsent(key(rule.system(), rule.code()), rule) != null) {
        throw invalid(source, label + ": duplicate code system + code");
      }
    }
    return Map.copyOf(byCode);
  }

  private static List<String> problemsWith(CoverageRule rule) {
    List<String> problems = new ArrayList<>();
    if (isBlank(rule.system()) || !isAbsoluteUri(rule.system())) {
      problems.add("system must be an absolute URI");
    }
    if (isBlank(rule.code())) {
      problems.add("code is required");
    }
    if (rule.paNeeded() == null || rule.docNeeded() == null || rule.emergencyExempt() == null) {
      problems.add("paNeeded, docNeeded and emergencyExempt are required");
      return problems;
    }
    if (rule.docNeeded() && !rule.paNeeded()) {
      problems.add("docNeeded needs paNeeded");
    }
    if (rule.docNeeded()
        && (isBlank(rule.questionnaire()) || !isAbsoluteUri(rule.questionnaire()))) {
      problems.add("docNeeded needs a questionnaire canonical URL");
    }
    if (!rule.docNeeded() && rule.questionnaire() != null) {
      problems.add("questionnaire is only allowed when docNeeded");
    }
    if (!rule.autoApproveCriteria().isEmpty() && !rule.docNeeded()) {
      problems.add("autoApproveCriteria need docNeeded, because they test questionnaire answers");
    }
    if (rule.emergencyExempt() && !rule.paNeeded()) {
      problems.add("emergencyExempt needs paNeeded");
    }
    for (ApprovalCriterion criterion : rule.autoApproveCriteria()) {
      if (criterion == null) {
        problems.add("autoApproveCriteria has an empty criterion");
      } else {
        problems.addAll(problemsWith(criterion));
      }
    }
    return problems;
  }

  private static List<String> problemsWith(ApprovalCriterion criterion) {
    List<String> problems = new ArrayList<>();
    String label = "criterion '" + criterion.linkId() + "'";
    if (isBlank(criterion.linkId())) {
      problems.add("criterion linkId is required");
    }
    if (criterion.operator() == null) {
      problems.add(label + " needs an operator (EQUALS, AT_LEAST or AT_MOST)");
    }
    if (isBlank(criterion.value())) {
      problems.add(label + " needs a value");
    } else if (criterion.operator() != null
        && criterion.isNumericOperator()
        && !ApprovalCriterion.isNumber(criterion.value())) {
      problems.add(label + " needs a numeric value for " + criterion.operator());
    }
    ApprovalCriterion.DenialReason reason = criterion.denialReason();
    if (reason != null && (isBlank(reason.code()) || isBlank(reason.text()))) {
      problems.add(label + " denialReason needs a code and a text");
    }
    return problems;
  }

  private static boolean isAbsoluteUri(String value) {
    try {
      return new URI(value).isAbsolute();
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String key(String system, String code) {
    return system + "|" + code;
  }

  private static IllegalStateException invalid(String source, String problem) {
    return new IllegalStateException("Invalid coverage rules in " + source + ": " + problem);
  }

  record CoverageRules(List<CoverageRule> rules) {

    CoverageRules {
      rules = rules == null ? List.of() : rules;
    }
  }
}
