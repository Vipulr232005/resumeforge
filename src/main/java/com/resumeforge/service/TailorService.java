package com.resumeforge.service;

import com.resumeforge.model.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
public class TailorService {
    private static final Set<String> COMMON_STOPWORDS = Set.of(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "is", "was", "are", "be", "been", "being", "have", "has", "had",
        "do", "does", "did", "will", "would", "could", "should", "may", "might",
        "can", "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they",
        "with", "from", "about", "into", "our", "not", "also", "such", "its",
        "looking", "seeking", "must", "plus", "strong", "experience", "years", "skills",
        "needed", "required", "preferred", "ability", "knowledge", "understanding",
        "work", "working", "team", "role", "join", "ideal", "candidate",
        "responsible", "responsibilities", "requirements", "qualifications",
        "including", "using", "across", "within", "between", "through",
        "well", "new", "best", "good", "great", "excellent", "proven",
        "highly", "deeply", "based", "related", "etc", "like"
    );

    public TailoringResult tailor(Resume resume, String jdText) {
        // Extract keywords from JD
        Set<String> jdKeywords = extractKeywords(jdText);

        // Score and reorder sections
        Resume tailoredResume = tailorResume(resume, jdKeywords);

        // Analyze coverage
        Set<String> coveredKeywords = findCoveredKeywords(tailoredResume, jdKeywords);
        Set<String> missingKeywords = new HashSet<>(jdKeywords);
        missingKeywords.removeAll(coveredKeywords);

        // Find gaps (skills/tools mentioned in JD but not in resume)
        Set<String> gaps = findGaps(resume, jdKeywords);

        return new TailoringResult(tailoredResume, new ArrayList<>(coveredKeywords), new ArrayList<>(missingKeywords), new ArrayList<>(gaps));
    }

    private Set<String> extractKeywords(String jd) {
        Set<String> keywords = new HashSet<>();
        String[] tokens = jd.toLowerCase().split("\\s+|[,;()]");

        for (String token : tokens) {
            String clean = token.replaceAll("[^a-z0-9+#]", "");
            if (clean.length() > 2 && !COMMON_STOPWORDS.contains(clean)) {
                keywords.add(clean);
            }
        }

        // Extract common phrases (2-3 word n-grams)
        Pattern phrasePattern = Pattern.compile("\\b([a-z]+ [a-z]+ [a-z]+|[a-z]+ [a-z]+)\\b");
        Matcher matcher = phrasePattern.matcher(jd.toLowerCase());
        while (matcher.find()) {
            String phrase = matcher.group();
            String[] words = phrase.split(" ");
            long nonStop = Arrays.stream(words).filter(w -> !COMMON_STOPWORDS.contains(w)).count();
            if (nonStop >= 2) {
                keywords.add(phrase);
            }
        }

        return keywords;
    }

    private Resume tailorResume(Resume resume, Set<String> jdKeywords) {
        List<String> tailoredSkills = reorderSkills(resume.skills(), jdKeywords);
        List<Experience> tailoredExperience = reorderExperience(resume.experience(), jdKeywords);
        List<Project> tailoredProjects = reorderProjects(resume.projects(), jdKeywords);

        return new Resume(
            resume.contact(),
            resume.summary(),
            tailoredSkills,
            tailoredExperience,
            resume.education(),
            tailoredProjects,
            resume.certifications()
        );
    }

    private List<String> reorderSkills(List<String> skills, Set<String> jdKeywords) {
        if (skills == null) return null;

        return skills.stream()
            .sorted((s1, s2) -> {
                int score1 = matchScore(s1, jdKeywords);
                int score2 = matchScore(s2, jdKeywords);
                return Integer.compare(score2, score1); // descending
            })
            .collect(Collectors.toList());
    }

    private List<Experience> reorderExperience(List<Experience> experiences, Set<String> jdKeywords) {
        if (experiences == null) return null;

        return experiences.stream()
            .map(exp -> reorderExperienceBullets(exp, jdKeywords))
            .sorted((e1, e2) -> {
                int score1 = matchScore(experienceToString(e1), jdKeywords);
                int score2 = matchScore(experienceToString(e2), jdKeywords);
                return Integer.compare(score2, score1);
            })
            .collect(Collectors.toList());
    }

    private Experience reorderExperienceBullets(Experience exp, Set<String> jdKeywords) {
        if (exp.bullets() == null) return exp;

        List<String> reorderedBullets = exp.bullets().stream()
            .sorted((b1, b2) -> {
                int score1 = matchScore(b1, jdKeywords);
                int score2 = matchScore(b2, jdKeywords);
                return Integer.compare(score2, score1);
            })
            .collect(Collectors.toList());

        return new Experience(exp.title(), exp.company(), exp.startDate(), exp.endDate(), exp.summary(), reorderedBullets);
    }

    private List<Project> reorderProjects(List<Project> projects, Set<String> jdKeywords) {
        if (projects == null) return null;

        return projects.stream()
            .sorted((p1, p2) -> {
                int score1 = matchScore(projectToString(p1), jdKeywords);
                int score2 = matchScore(projectToString(p2), jdKeywords);
                return Integer.compare(score2, score1);
            })
            .collect(Collectors.toList());
    }

    private int matchScore(String text, Set<String> keywords) {
        if (text == null) return 0;
        String lower = text.toLowerCase();
        int score = 0;

        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                score += keyword.split("\\s+", -1).length; // longer phrases = higher score
            }
        }

        return score;
    }

    private Set<String> findCoveredKeywords(Resume resume, Set<String> jdKeywords) {
        Set<String> covered = new HashSet<>();
        String resumeText = serializeResume(resume).toLowerCase();

        for (String keyword : jdKeywords) {
            if (resumeText.contains(keyword)) {
                covered.add(keyword);
            }
        }

        return covered;
    }

    private Set<String> findGaps(Resume resume, Set<String> jdKeywords) {
        Set<String> gaps = new HashSet<>();
        String resumeText = serializeResume(resume).toLowerCase();

        for (String keyword : jdKeywords) {
            if (!resumeText.contains(keyword)) {
                // Only flag if it looks like a skill/tool (no spaces or technical term)
                if (!keyword.contains(" ") || isTechnicalTerm(keyword)) {
                    gaps.add(keyword);
                }
            }
        }

        return gaps;
    }

    private boolean isTechnicalTerm(String term) {
        return term.contains("+") || term.contains("#") || term.length() <= 6;
    }

    private String serializeResume(Resume resume) {
        StringBuilder sb = new StringBuilder();
        if (resume.skills() != null) sb.append(String.join(" ", resume.skills())).append(" ");
        if (resume.experience() != null) {
            for (var exp : resume.experience()) {
                if (exp.bullets() != null) sb.append(String.join(" ", exp.bullets())).append(" ");
            }
        }
        return sb.toString();
    }

    private String experienceToString(Experience exp) {
        StringBuilder sb = new StringBuilder();
        if (exp.title() != null) sb.append(exp.title()).append(" ");
        if (exp.company() != null) sb.append(exp.company()).append(" ");
        if (exp.bullets() != null) sb.append(String.join(" ", exp.bullets()));
        return sb.toString();
    }

    private String projectToString(Project proj) {
        StringBuilder sb = new StringBuilder();
        if (proj.title() != null) sb.append(proj.title()).append(" ");
        if (proj.description() != null) sb.append(proj.description()).append(" ");
        if (proj.technologies() != null) sb.append(String.join(" ", proj.technologies()));
        return sb.toString();
    }

    public record TailoringResult(
        Resume resume,
        List<String> covered,
        List<String> missing,
        List<String> gaps
    ) {
    }
}
