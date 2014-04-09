package com.palominolabs.gradle.task.git.clone

import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail

final class GitCloneTaskTest {

  public static final String SSH_REPO_URI = 'git@github.com:palominolabs/gradle-git-clone-task-demo-repo.git'
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  File dir

  GitCloneTask task

  @Before
  public void setUp() throws IOException {
    dir = tmp.newFolder()

    task = ProjectBuilder.builder().build().task('cloneTask', type: GitCloneTask)
    task.dir = dir
    task.uri = 'https://github.com/palominolabs/gradle-git-clone-task-demo-repo.git'
    task.treeish = 'b81f11dac85d93566036cb5d31d4e8365752e9f6'
  }

  @Test
  public void testClonesIntoExistingDir() {
    task.setUpRepo()

    assertChangedFileContents("v3")
  }

  @Test
  public void testCreatesDirAndClones() {
    task.dir = new File(dir, "subdir")
    task.setUpRepo()

    assertChangedFileContents("v3")
  }

  @Test
  public void testCleansUpDirtyFilesIfRequested() {
    task.reset = true
    task.setUpRepo()

    new File(task.dir, "file1").setText "new text!"

    task.setUpRepo()

    assertChangedFileContents("v3")
  }

  @Test
  public void testDoesntCleanUpDirtyFilesIfDisabled() {
    task.reset = false
    task.setUpRepo()

    new File(task.dir, "file1").setText "new text!"

    assertChangedFileContents("new text!")
  }

  @Test
  public void testCanGoToOldCommitInRepo() {
    task.setUpRepo()
    assertChangedFileContents("v3")

    task.treeish = 'e3e07ab45915482d4f7ac061caef3dd684f2c132'
    task.setUpRepo()

    assertChangedFileContents("v1")
  }

  @Test
  public void testChecksOutTag() {
    task.treeish = 'v2'
    task.setUpRepo()

    assertChangedFileContents("v2")
  }

  @Test
  public void testChecksOutBranch() {
    task.treeish = 'origin/branch-at-v2'
    task.setUpRepo()

    assertChangedFileContents("v2")
  }

  @Test
  public void testChecksOutHash() {
    task.treeish = '4822b45c4599786240255898c1c1ad4ecdc50a2c'
    task.setUpRepo()

    assertChangedFileContents("v2")
  }

  @Test
  public void testSshUri() {
    task.uri = SSH_REPO_URI

    task.setUpRepo()
    assertChangedFileContents("v3")
  }

  @Test
  public void testUseUnknownTreeishFails() {
    task.treeish = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    try {
      task.setUpRepo()
      fail()
    } catch (RuntimeException e) {
      // this is only thrown after trying to fetch. Unfortunately, there isn't a great way to test
      // that trying again will actually *work*; that would involve making a repo publicly writable
      // so that everyone running the tests could pass it.
      assertEquals("Couldn't resolve <xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx>", e.getMessage())
    }
  }

  @Test
  public void testUsePrivKeyFile() {
    task.uri = SSH_REPO_URI
    task.trySshAgent = false
    task.sshIdentityPrivKeyPath = new File('.').canonicalFile.toPath().resolve("src/test/resources/id_rsa").toString()

    task.setUpRepo()
    assertChangedFileContents("v3")
  }

  @Test
  @Ignore
  public void testFallBackToIdentityIfSshAgentNotFound() {
    task.uri = SSH_REPO_URI

    Map<String, String> env = System.getenv()
    String envVar = 'SSH_AUTH_SOCK'
    String origValue = env.get(envVar)

    POSIX posix = POSIXFactory.getPOSIX()
    if (origValue != null) {
      posix.unsetenv(envVar)
    }

    try {
      task.sshIdentityPrivKeyPath = '/foo/bar'

      task.setUpRepo()
    } finally {
      if (origValue != null) {
        posix.setenv(envVar, origValue, 1)
      }
    }

    assertChangedFileContents("v3")
  }

  void assertChangedFileContents(String contents) {
    assertFileContents(new File(task.dir, "file1"), contents)
  }

  static void assertFileContents(File f, String contents) {
    assert f.exists()

    assert contents == f.text.trim()
  }
}
