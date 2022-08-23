//package com.hedera.services.bdd.spec.verification;
//
//import com.google.common.collect.HashMultimap;
//import com.google.common.collect.Multimap;
//import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
//import com.hedera.services.bdd.spec.verification.traceability.MismatchedSidecar;
//import com.hedera.services.recordstreaming.RecordStreamingUtils;
//import com.hedera.services.stream.proto.RecordStreamFile;
//import java.util.Queue;
//import java.util.concurrent.LinkedBlockingDeque;
//import java.util.function.Consumer;
//import java.util.regex.Pattern;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.io.IOException;
//import java.nio.file.FileSystems;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.nio.file.StandardWatchEventKinds;
//import java.nio.file.WatchEvent;
//import java.nio.file.WatchKey;
//import java.nio.file.WatchService;
//
//public class RecordFileDirectoryWatcher {
//
//  private static final Logger log = LogManager.getLogger(RecordFileDirectoryWatcher.class);
//  private static final Pattern RECORD_FILE_REGEX =
//      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.\\d{9}Z.rcd");
//
//
//  private final Path recordStreamFolderPath;
//  private WatchService watchService;
//  private final Queue<ExpectedSidecar> expectedSidecars = new LinkedBlockingDeque<>();
//
//  private boolean shouldTerminateAfterNextSidecar = false;
//  private boolean hasSeenFirst = false;
//
//  public RecordFileDirectoryWatcher(Path recordStreamFolderPath) {
//    this.recordStreamFolderPath = recordStreamFolderPath;
//  }
//
//  public void prepareInfrastructure() throws IOException {
//    watchService = FileSystems.getDefault().newWatchService();
//    recordStreamFolderPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
//  }
//
//  public void watch() throws IOException {
//    for (; ; ) {
//      // wait for key to be signaled
//      WatchKey key;
//      try {
//        key = watchService.take();
//      } catch (InterruptedException x) {
//        Thread.currentThread().interrupt();
//        return;
//      }
//      for (final var event : key.pollEvents()) {
//        final var kind = event.kind();
//        // This key is registered only
//        // for ENTRY_CREATE events,
//        // but an OVERFLOW event can
//        // occur regardless if events
//        // are lost or discarded.
//        if (kind == StandardWatchEventKinds.OVERFLOW) {
//          continue;
//        }
//        // The filename is the
//        // context of the event.
//        final var ev = (WatchEvent<Path>) event;
//        final var filename = ev.context();
//        final var child = recordStreamFolderPath.resolve(filename);
//        log.debug("Record stream file created -> {} ", child.getFileName());
//        final var newFilePath = child.toAbsolutePath().toString();
//        if (RECORD_FILE_REGEX.matcher(newFilePath).find()) {
//          final var pair = RecordStreamingUtils.readRecordStreamFile(String.valueOf(child.toAbsolutePath()));
//          if (pair.getLeft() == -1) {
//            throw new RuntimeException();
//          }
//          recordStreamFileConsumer.accept(pair.getRight().orElseThrow());
//        }
//      }
//
//      // Reset the key -- this step is critical if you want to
//      // receive further watch events.  If the key is no longer valid,
//      // the directory is inaccessible so exit the loop.
//      boolean valid = key.reset();
//      if (!valid) {
//        break;
//      }
//    }
//  }
//
//  public void tearDown() {
//    try {
//      watchService.close();
//    } catch (IOException e) {
//      log.warn("Watch service couldn't be closed.");
//    }
//  }
//}
