package codes.ani.ares.ingestion;

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
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GraphifyRefinery {
    private final AresBlockRepository aresBlockRepository;
    private final ObjectMapper objectMapper;

    public void refine(UUID jobId, Path repoPath, String projectId) {
        log.info("Starting graphify for jobId: {}, repoPath: {}, projectId: {}", jobId, repoPath, projectId);

        try {
            ProcessBuilder pb = new ProcessBuilder("graphify", ".");
            pb.directory(repoPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Graphify failed with exit code: " + exitCode);
            }
            log.info("Graphify completed successfully for jobId: {}", jobId);

            File graphFile = repoPath.resolve("graph.json").toFile();
            JsonNode rootNode = objectMapper.readTree(graphFile);

            List<AresBlock> blocks = new ArrayList<>();
            rootNode.get("nodes").forEach(node -> {
                blocks.add(AresBlock.builder()
                        .jobId(jobId)
                        .projectId(projectId)
                        .type(AresBlockType.CODE)
                        .filePath(node.get("file").asText())
                        .content(node.get("content").asText())
                        .hash(node.get("signature").asText())
                        .metadata(objectMapper.convertValue(node, Map.class))
                        .build());
            });

            aresBlockRepository.saveAll(blocks);
            log.info("Successfully refined graphify output for jobId: {}, total blocks: {}", jobId, blocks.size());
        } catch (Exception e) {
            log.error("Error during graphify refinement for jobId: {}, repoPath: {}, projectId: {}. Error: {}", jobId, repoPath, projectId, e.getMessage());
            throw new RuntimeException("Graphify Refinement Error", e);
        }
    }
}
