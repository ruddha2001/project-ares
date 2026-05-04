package codes.ani.ares.ingestion;

import codes.ani.ares.job.AresJobService;
import codes.ani.ares.model.AresBlock;
import codes.ani.ares.model.AresBlockType;
import codes.ani.ares.repository.AresBlockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GraphifyRefinery {
    private final AresBlockRepository aresBlockRepository;
    private final ObjectMapper objectMapper;
    private final AresJobService aresJobService;

    public void refine(UUID jobId, Path repoPath, String projectId) {
        log.info("Starting graphify for jobId: {}, root: {}, projectId: {}", jobId, repoPath, projectId);
        aresJobService.addLog(jobId, "Starting graphify refinement for repository at: " + repoPath.toAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder("graphify", "update", ".");
            pb.directory(repoPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Graphify failed for job {}. Exit code: {}. Output: \n{}", jobId, exitCode, output);
                throw new RuntimeException("Graphify failed with exit code: " + exitCode);
            }
            log.info("Graphify completed successfully for jobId: {}", jobId);
            aresJobService.addLog(jobId, "Graphify completed successfully for jobId: " + jobId);

            File graphFile = repoPath.resolve("graphify-out").resolve("graph.json").toFile();
            JsonNode rootNode = objectMapper.readTree(graphFile);

            List<AresBlock> blocks = new ArrayList<>();
            rootNode.get("nodes").forEach(node -> {
                String filePath = node.path("source_file").asText();
                String nodeId = node.path("id").asText();
                String fileType = node.path("file_type").asText();

                if (filePath != null && "code".equals(fileType)) {
                    try {
                        String content = resolveAndReadFile(repoPath, filePath);

                        blocks.add(AresBlock.builder()
                                .jobId(jobId)
                                .projectId(projectId)
                                .type(AresBlockType.CODE)
                                .filePath(filePath)
                                .content(content)
                                .hash(nodeId)
                                .metadata(objectMapper.convertValue(node, java.util.Map.class))
                                .build());

                    } catch (Exception e) {
                        log.warn("Could not index node {}: {}", nodeId, e.getMessage());
                    }
                }
            });

            aresBlockRepository.saveAll(blocks);
            log.info("Successfully refined graphify output for jobId: {}, total blocks: {}", jobId, blocks.size());
            aresJobService.addLog(jobId, "Successfully refined graphify output, total blocks: " + blocks.size());
        } catch (Exception e) {
            log.error("Error during graphify refinement for jobId: {}, repoPath: {}, projectId: {}. Error: {}", jobId, repoPath, projectId, e.getMessage());
            throw new RuntimeException("Graphify Refinement Error", e);
        }
    }

    private String resolveAndReadFile(Path root, String logicalPath) throws java.io.IOException {
        try (var stream = java.nio.file.Files.walk(root)) {
            return stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(logicalPath))
                    .findFirst()
                    .map(p -> {
                        try {
                            return java.nio.file.Files.readString(p);
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .orElseThrow(() -> new java.io.FileNotFoundException("Physical file not found for: " + logicalPath));
        }
    }
}