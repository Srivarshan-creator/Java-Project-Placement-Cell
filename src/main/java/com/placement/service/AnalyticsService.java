package com.placement.service;

import com.placement.dto.CompanyOfferMetricDto;
import com.placement.dto.DepartmentMetricDto;
import com.placement.dto.PlacementDashboardMetricsDto;
import com.placement.model.ApplicationStatus;
import com.placement.model.Student;
import com.placement.repository.CompanyRepository;
import com.placement.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final StudentRepository studentRepository;
    private final CompanyRepository companyRepository;

    public PlacementDashboardMetricsDto getPlacementDashboardMetrics() {
        List<Student> allStudents = studentRepository.findAll();
        long totalStudents = allStudents.size();
        long totalPlaced = allStudents.stream().filter(Student::isPlaced).count();
        double overallPlacementPct = totalStudents > 0
                ? round((double) totalPlaced * 100.0 / totalStudents, 1)
                : 0.0;

        List<Student> placedStudents = allStudents.stream()
                .filter(s -> s.isPlaced() && s.getOfferLpa() != null && s.getOfferLpa() > 0)
                .toList();

        double overallAvg = placedStudents.isEmpty() ? 0.0 :
                round(placedStudents.stream().mapToDouble(Student::getOfferLpa).average().orElse(0.0), 2);

        double overallMax = placedStudents.isEmpty() ? 0.0 :
                round(placedStudents.stream().mapToDouble(Student::getOfferLpa).max().orElse(0.0), 2);

        List<DepartmentMetricDto> departmentMetrics = computeDepartmentMetrics();
        List<CompanyOfferMetricDto> companyOfferMetrics = computeCompanyOfferMetrics();
        List<Student> unplacedStudents = studentRepository.findByPlaced(false);

        return PlacementDashboardMetricsDto.builder()
                .totalStudents(totalStudents)
                .totalPlaced(totalPlaced)
                .overallPlacementPercentage(overallPlacementPct)
                .overallAvgPackage(overallAvg)
                .overallHighestPackage(overallMax)
                .departmentMetrics(departmentMetrics)
                .companyOfferMetrics(companyOfferMetrics)
                .unplacedStudents(unplacedStudents)
                .build();
    }

    public List<DepartmentMetricDto> computeDepartmentMetrics() {
        // Pipeline 1: Group all students by branch to get total and placed counts
        Aggregation countAgg = newAggregation(
                group("branch")
                        .count().as("totalStudents")
                        .sum(ConditionalOperators.when(Criteria.where("placed").is(true)).then(1).otherwise(0)).as("placedCount")
        );

        AggregationResults<Document> countResults = mongoTemplate.aggregate(countAgg, "students", Document.class);

        // Pipeline 2: Group placed students with valid offerLpa by branch to get avg and max packages
        Aggregation packageAgg = newAggregation(
                match(Criteria.where("placed").is(true).and("offerLpa").gt(0)),
                group("branch")
                        .avg("offerLpa").as("avgPackage")
                        .max("offerLpa").as("maxPackage")
        );

        AggregationResults<Document> packageResults = mongoTemplate.aggregate(packageAgg, "students", Document.class);

        Map<String, double[]> packageMap = new HashMap<>();
        for (Document doc : packageResults.getMappedResults()) {
            String branch = doc.getString("_id");
            if (branch == null || branch.isBlank()) {
                branch = "OTHER";
            }
            double avg = ((Number) doc.getOrDefault("avgPackage", 0.0)).doubleValue();
            double max = ((Number) doc.getOrDefault("maxPackage", 0.0)).doubleValue();
            packageMap.put(branch.toUpperCase(), new double[]{round(avg, 2), round(max, 2)});
        }

        List<DepartmentMetricDto> metrics = new ArrayList<>();
        for (Document doc : countResults.getMappedResults()) {
            String branch = doc.getString("_id");
            if (branch == null || branch.isBlank()) {
                branch = "OTHER";
            }
            long total = ((Number) doc.getOrDefault("totalStudents", 0)).longValue();
            long placed = ((Number) doc.getOrDefault("placedCount", 0)).longValue();
            double pct = total > 0 ? round((double) placed * 100.0 / total, 1) : 0.0;

            double[] pkg = packageMap.getOrDefault(branch.toUpperCase(), new double[]{0.0, 0.0});

            metrics.add(DepartmentMetricDto.builder()
                    .branch(branch)
                    .totalStudents(total)
                    .placedCount(placed)
                    .placementPercentage(pct)
                    .averagePackage(pkg[0])
                    .highestPackage(pkg[1])
                    .build());
        }

        metrics.sort((a, b) -> a.getBranch().compareToIgnoreCase(b.getBranch()));
        return metrics;
    }

    public List<CompanyOfferMetricDto> computeCompanyOfferMetrics() {
        Aggregation agg = newAggregation(
                match(Criteria.where("status").is(ApplicationStatus.OFFERED.name())),
                group("companyId").count().as("offerCount"),
                sort(Sort.Direction.DESC, "offerCount")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "applications", Document.class);
        List<CompanyOfferMetricDto> list = new ArrayList<>();

        Map<String, String> companyNames = new HashMap<>();
        companyRepository.findAll().forEach(c -> companyNames.put(c.getId(), c.getName()));

        for (Document doc : results.getMappedResults()) {
            String companyId = doc.getString("_id");
            long count = ((Number) doc.getOrDefault("offerCount", 0)).longValue();
            String name = companyNames.getOrDefault(companyId, "Company #" + companyId);

            list.add(CompanyOfferMetricDto.builder()
                    .companyId(companyId)
                    .companyName(name)
                    .offerCount(count)
                    .build());
        }

        return list;
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
