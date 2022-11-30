package services.gui;

import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.MessagesStrings;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.*;
import play.Logger;
import play.db.jpa.JPAApi;
import scala.Option;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service class around ComponentResults and StudyResults. It's used by controllers or other services.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("ALL")
@Singleton
public class ResultService {

    private static final Logger.ALogger LOGGER = Logger.of(ResultService.class);

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final JsonUtils jsonUtils;
    private final Checker checker;
    private final StudyLogger studyLogger;
    private final JPAApi jpaApi;

    @Inject
    ResultService(ComponentResultDao componentResultDao, StudyResultDao studyResultDao, JsonUtils jsonUtils,
            Checker checker, StudyLogger studyLogger, JPAApi jpaApi) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.jsonUtils = jsonUtils;
        this.checker = checker;
        this.studyLogger = studyLogger;
        this.jpaApi = jpaApi;
    }

    /**
     * Gets the corresponding ComponentResult for a list of IDs. Throws an exception if the ComponentResult doesn't
     * exist.
     */
    public List<ComponentResult> getComponentResults(List<Long> componentResultIdList) throws NotFoundException {
        List<ComponentResult> componentResultList = new ArrayList<>();
        for (Long componentResultId : componentResultIdList) {
            ComponentResult componentResult = componentResultDao.findById(componentResultId);
            if (componentResult == null) {
                throw new NotFoundException(MessagesStrings.componentResultNotExist(componentResultId));
            }
            componentResultList.add(componentResult);
        }
        return componentResultList;
    }

    /**
     * Get all StudyResults or throw an Exception if one doesn't exist. Throws an exception if the StudyResult doesn't
     * exist.
     */
    public List<StudyResult> getStudyResults(List<Long> studyResultIdList) throws NotFoundException {
        List<StudyResult> studyResultList = new ArrayList<>();
        for (Long studyResultId : studyResultIdList) {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            if (studyResult == null) {
                throw new NotFoundException(MessagesStrings.studyResultNotExist(studyResultId));
            }
            studyResultList.add(studyResult);
        }
        return studyResultList;
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given Study
     * from the database.
     */
    public Source<ByteString, ?> streamStudyResultsByStudy(Study study) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchStudyResultsByStudyPaginated(writer, study);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamStudyResultsByStudy: ", e);
                    }
                }));
    }

    private void fetchStudyResultsByStudyPaginated(Writer writer, Study study) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByStudy(study);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByStudy(study, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given Batch and worker
     * type from the database. If the worker type is empty it returns all results of this Batch.
     */
    public Source<ByteString, ?> streamStudyResultsByBatch(Option<String> workerType, Batch batch) {
        if (workerType.isEmpty()) {
            return StreamConverters.asOutputStream()
                    .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                    .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                        try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                            writer.write("[");
                            fetchStudyResultsByBatchPaginated(writer, batch);
                            writer.write("]");
                            writer.flush();
                        } catch (Exception e) {
                            LOGGER.error(".streamStudyResultsByBatch: ", e);
                        }
                    }));
        } else {
            return StreamConverters.asOutputStream()
                    .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                    .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                        try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                            writer.write("[");
                            fetchStudyResultsByBatchAndWorkerTypePaginated(writer, batch, workerType.get());
                            // If worker type is MT then add MTSandbox on top
                            if (MTWorker.WORKER_TYPE.equals(workerType.get())) {
                                fetchStudyResultsByBatchAndWorkerTypePaginated(writer, batch, MTSandboxWorker.WORKER_TYPE);
                            }
                            writer.write("]");
                            writer.flush();
                        } catch (Exception e) {
                            LOGGER.error(".streamStudyResultsByBatch: ", e);
                        }
                    }));
        }
    }

    private void fetchStudyResultsByBatchPaginated(Writer writer, Batch batch) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByBatch(batch, JatosWorker.WORKER_TYPE);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByBatch(batch, JatosWorker.WORKER_TYPE, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    private void fetchStudyResultsByBatchAndWorkerTypePaginated(Writer writer, Batch batch, String workerType) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByBatchAndWorkerType(batch, workerType);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao
                        .findAllByBatchAndWorkerType(batch, workerType, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given GroupResult
     * from the database.
     */
    public Source<ByteString, ?> streamStudyResultsByGroup(GroupResult groupResult) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchStudyResultsByGroupPaginated(writer, groupResult);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamStudyResultsByGroup: ", e);
                    }
                }));
    }

    private void fetchStudyResultsByGroupPaginated(Writer writer, GroupResult group) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByGroup(group);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByGroup(group, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream StudyResults (including their result data) that belong to the given Worker
     * from the database.
     */
    public Source<ByteString, ?> streamStudyResultsByWorker(User loggedInUser, Worker worker) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchStudyResultsByWorkerPaginated(writer, worker, loggedInUser);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamStudyResultsByWorker: ", e);
                    }
                }));
    }

    private void fetchStudyResultsByWorkerPaginated(Writer writer, Worker worker, User user) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByWorker(worker, user);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByWorker(worker, user, first, maxDbQuerySize);
                Errors.rethrow().run(() -> writeStudyResults(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Uses a Akka Source to stream ComponentResults (including their result data) that belong to the given Component
     * from the database.
     */
    public Source<ByteString, ?> streamComponentResults(Component component) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write("[");
                        fetchComponentResultsPaginated(writer, component);
                        writer.write("]");
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamComponentResults: ", e);
                    }
                }));
    }

    private void fetchComponentResultsPaginated(Writer writer, Component component) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return componentResultDao.countByComponent(component);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<ComponentResult> resultList = componentResultDao.findAllByComponent(component, first,
                        maxDbQuerySize);
                Errors.rethrow().run(() -> writeComponentResult(writer, isLastPage, resultList));
            });
        }
    }

    /**
     * Returns an Akka Source that streams all data of the given component results specified by their IDs.
     */
    public Source<ByteString, ?> streamComponentResultData(User loggedInUser, List<Long> componentResultIdList) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        fetchComponentResultDataByIds(writer, componentResultIdList, loggedInUser);
                        writer.flush();
                    } catch (Exception e) {
                        LOGGER.error(".streamComponentResult: ", e);
                    }
                }));
    }

    /**
     * Returns a File that contains all data of the given component results specified by their IDs.
     */
    public File getComponentResultDataAsTmpFile(User loggedInUser, List<Long> componentResultIdList) {
        File tmpFile = new File(IOUtils.TMP_DIR, "JatosExport_" + UUID.randomUUID());
        try (Writer writer = new BufferedWriter(new FileWriter(tmpFile, true))) {
            fetchComponentResultDataByIds(writer, componentResultIdList, loggedInUser);
            writer.flush();
        } catch (IOException e) {
            LOGGER.error(".streamComponentResult: ", e);
            tmpFile.delete();
        }
        return tmpFile;
    }

    /**
     * Fetches the ComponentResults that correspond to the IDs, checks them and writes their result data into the given
     * Writer. Fetches them one by one to reduce memory usages.
     */
    private void fetchComponentResultDataByIds(Writer writer, List<Long> componentResultIdList, User user) {
        Set<Study> studies = new HashSet<>();
        for (Long componentResultId : componentResultIdList) {
            jpaApi.withTransaction(entityManager -> {
                ComponentResult componentResult = componentResultDao.findById(componentResultId);
                if (componentResult != null) {
                    Errors.rethrow().run(() -> checker.checkComponentResult(componentResult, user, false));
                    studies.add(componentResult.getStudyResult().getStudy());
                    Errors.rethrow().run(() -> writeComponentResultData(writer, componentResult));
                } else {
                    LOGGER.warn("A component result with ID " + componentResultId + " doesn't exist.");
                }
            });
        }
        studies.forEach(study -> studyLogger.log(study, user, "Exported result data to file"));
    }

    private void writeStudyResults(Writer writer, boolean isLastPage, List<StudyResult> resultList) throws IOException {
        List<Long> srids = resultList.stream().map(StudyResult::getId).collect(Collectors.toList());
        Map<Long, Integer> componentResultCounts = studyResultDao.countComponentResultsForStudyResultIds(srids);
        for (int i = 0; i < resultList.size(); i++) {
            StudyResult result = resultList.get(i);
            Integer componentResultCount = componentResultCounts.get(result.getId());
            JsonNode resultNode = jsonUtils.studyResultAsJsonNode(result, componentResultCount);
            writer.write(resultNode.toString());
            boolean isLastResult = (i + 1) >= resultList.size();
            if (!isLastPage || !isLastResult) {
                writer.write(",\n");
            }
        }
    }

    private void writeComponentResult(Writer writer, boolean isLastPage, List<ComponentResult> resultList)
            throws IOException {
        for (int j = 0; j < resultList.size(); j++) {
            ComponentResult result = resultList.get(j);
            JsonNode resultNode = jsonUtils.componentResultAsJsonNode(result, true);
            writer.write(resultNode.toString());
            boolean isLastResult = (j + 1) >= resultList.size();
            if (!isLastPage || !isLastResult) {
                writer.write(",\n");
            }
        }
    }

    private void writeComponentResultData(Writer writer,
            ComponentResult componentResult) throws IOException, SQLException {
        String resultData = componentResultDao.getData(componentResult.getId());
        if (resultData == null) return;
        writer.write(resultData + System.lineSeparator());
    }

    /**
     * This method is only used during update from version <3.7.5. It creates for each existing
     * ComponentResult entity the fields dataShort and dataSize.
     */
    public void fillDataFieldsForExistingComponentResults() {
        List<Long> crids = jpaApi.withTransaction(componentResultDao::findAllIdsWhereDataSizeIsNull);
        LOGGER.info("Start filling dataSize and dataShort fields of ComponentResults. This is part of the update " +
                "and can take a while depending on the number of ComponentResults in your database.");
        crids.parallelStream().forEach(crid -> {
            jpaApi.withTransaction(() -> {
                Errors.rethrow().run(() -> componentResultDao.setDataSizeAndDataShort(crid));
                LOGGER.info("Filled dataSize and dataShort fields of ComponentResult " + crid);
            });
        });
        if (!crids.isEmpty()) {
            LOGGER.info("Filled dataSize and dataShort fields in " + crids.size() + " ComponentResult entities");
        }
    }

}
