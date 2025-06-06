/*
 * Copyright 2013-2024, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.util

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path

import com.google.common.hash.Hashing
import com.google.common.hash.Hasher
import com.google.common.hash.HashCode
import nextflow.Global
import nextflow.Session
import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Specification
import test.TestHelper
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class HashBuilderTest extends Specification {


    def testHashContent() {
        setup:
        def path1 = Files.createTempFile('test-hash-content',null)
        def path2 = Files.createTempFile('test-hash-content',null)
        def path3 = Files.createTempFile('test-hash-content',null)

        path1.text = '''
            line 1
            line 2
            line 3 the file content
            '''


        path2.text = '''
            line 1
            line 2
            line 3 the file content
            '''

        path3.text = '''
            line 1
            line 1
            line 1 the file content
            '''

        expect:
        HashBuilder.hashContent(path1) == HashBuilder.hashContent(path2)
        HashBuilder.hashContent(path1) != HashBuilder.hashContent(path3)
        HashBuilder.hashContent(path1, Hashing.md5()) == HashBuilder.hashContent(path2,Hashing.md5())
        HashBuilder.hashContent(path1, Hashing.md5()) != HashBuilder.hashContent(path3,Hashing.md5())

        cleanup:
        path1.delete()
        path2.delete()
        path3.delete()

    }
    
    def 'should validate is asset file'() {
        when:
        def BASE = Paths.get("/some/pipeline/dir")
        and:
        Global.session = Mock(Session) { getBaseDir() >> BASE }
        then:
        !HashBuilder.isAssetFile(BASE.resolve('foo'))


        when:
        Global.session = Mock(Session) {
            getBaseDir() >> BASE
            getCommitId() >> '123456'
        }
        then:
        HashBuilder.isAssetFile(BASE.resolve('foo'))
        and:
        !HashBuilder.isAssetFile(Paths.get('/other/dir'))
    }

    def 'should hash file content'() {
        given:
        def EXPECTED = '64ec88ca00b268e5ba1a35678a1b5316d212f4f366b2477232534a8aeca37f3c'
        def file = TestHelper.createInMemTempFile('foo', 'Hello world')
        expect:
        HashBuilder.hashFileSha256Impl0(file) == EXPECTED
        and:
        HashBuilder.hashFileSha256Impl0(file) == DigestUtils.sha256Hex(file.bytes)
    }

    def 'should hash dir content with sha256'() {
        given:
        def folder = TestHelper.createInMemTempDir()
        folder.resolve('dir1').mkdir()
        folder.resolve('dir2').mkdir()
        and:
        folder.resolve('dir1/foo').text = "I'm foo"
        folder.resolve('dir1/bar').text = "I'm bar"
        folder.resolve('dir1/xxx/yyy').mkdirs()
        folder.resolve('dir1/xxx/foo1').text = "I'm foo within xxx"
        folder.resolve('dir1/xxx/yyy/bar1').text = "I'm bar within yyy"
        and:
        folder.resolve('dir2/foo').text = "I'm foo"
        folder.resolve('dir2/bar').text = "I'm bar"
        folder.resolve('dir2/xxx/yyy').mkdirs()
        folder.resolve('dir2/xxx/foo1').text = "I'm foo within xxx"
        folder.resolve('dir2/xxx/yyy/bar1').text = "I'm bar within yyy"

        when:
        def hash1 = HashBuilder.hashDirSha256(HashBuilder.defaultHasher(), folder.resolve('dir1'), folder.resolve('dir1'))
        and:
        def hash2 = HashBuilder.hashDirSha256(HashBuilder.defaultHasher(), folder.resolve('dir2'), folder.resolve('dir2'))

        then:
        hash1.hash() == hash2.hash()

    }

    @Unroll
    def 'hash should be consistent when using filesystem trick with 20+ files: #description'() {
        given: "Two directories to be filled with identical content using different strategies"
        Path dir1 = TestHelper.createInMemTempDir()
        Path dir2 = TestHelper.createInMemTempDir()
        int fileCount = 20

        and: "A 'control' setup creating files alphabetically"
        def controlSetup = { Path dir ->
            // Create 20 files in the root directory
            (1..fileCount).each { i ->
                dir.resolve(String.format("root-file-%02d.txt", i)).text = "content for root file ${i}"
            }
            // Create a subdirectory and 20 files within it
            def subdir = dir.resolve('subdir')
            subdir.mkdir()
            (1..fileCount).each { i ->
                subdir.resolve(String.format("sub-file-%02d.txt", i)).text = "content for sub file ${i}"
            }
        }

        and: "An 'experimental' setup that applies a filesystem trick"
        def experimentalSetup = setupClosure

        // Create the two directories using the defined setups
        controlSetup(dir1)
        experimentalSetup(dir2, fileCount)

        when: "Hashes for both directories are calculated"
        def hasher1 = HashBuilder.defaultHasher()
        HashBuilder.hashDirSha256(hasher1, dir1, dir1)
        def hash1 = hasher1.hash()

        def hasher2 = HashBuilder.defaultHasher()
        HashBuilder.hashDirSha256(hasher2, dir2, dir2)
        def hash2 = hasher2.hash()

        then: "The hashes must be identical, as the new code sorts paths internally"
        // The old, buggy code would fail here if the traversal order was successfully manipulated.
        hash1 == hash2

        where:
        description                      | setupClosure
        'Reverse Creation Order'         | { Path dir, int count ->
                                                // Technique: Create files in the reverse order of the control setup.
                                                // Goal: On simple filesystems, entry order might follow creation order.
                                                def subdir = dir.resolve('subdir')
                                                subdir.mkdir()
                                                // Create subdirectory files in reverse
                                                (count..1).each { i ->
                                                    subdir.resolve(String.format("sub-file-%02d.txt", i)).text = "content for sub file ${i}"
                                                }
                                                // Create root files in reverse
                                                (count..1).each { i ->
                                                    dir.resolve(String.format("root-file-%02d.txt", i)).text = "content for root file ${i}"
                                                }
                                            }

        'Delete and Re-create Middle'    | { Path dir, int count ->
                                                // Technique: Delete an entry from the middle of an alphabetical set and re-add it.
                                                // Goal: The re-added entry might be appended to the end of the directory list.
                                                def subdir = dir.resolve('subdir')
                                                subdir.mkdir()
                                                (1..count).each { i ->
                                                    dir.resolve(String.format("root-file-%02d.txt", i)).text = "content for root file ${i}"
                                                    subdir.resolve(String.format("sub-file-%02d.txt", i)).text = "content for sub file ${i}"
                                                }
                                                
                                                // Now, delete and re-create a file from the middle of the sequence
                                                def midFileIndex = count / 2
                                                def rootFile = dir.resolve(String.format("root-file-%02d.txt", midFileIndex))
                                                def subFile = subdir.resolve(String.format("sub-file-%02d.txt", midFileIndex))
                                                def rootContent = rootFile.text
                                                def subContent = subFile.text
                                                
                                                rootFile.delete()
                                                subFile.delete()
                                                
                                                rootFile.text = rootContent
                                                subFile.text = subContent
                                            }

        'Rename-Shuffle'                 | { Path dir, int count ->
                                                // Technique: Create files with temporary names and rename them in a non-alphabetical order.
                                                // Goal: The final directory entry order might be influenced by the timing of the rename operations.
                                                def tmpDir = dir.resolve('__tmp')
                                                tmpDir.mkdir()
                                                def tmpSubDir = tmpDir.resolve('__subdir')
                                                tmpSubDir.mkdir()
                                                
                                                // Create all files with temporary names
                                                (1..count).each { i ->
                                                    tmpDir.resolve("tmp-root-${i}").text = "content for root file ${i}"
                                                    tmpSubDir.resolve("tmp-sub-${i}").text = "content for sub file ${i}"
                                                }
                                                
                                                // Rename them to their final destination in reverse order
                                                def finalSubDir = dir.resolve('subdir')
                                                finalSubDir.mkdir()
                                                (count..1).each { i ->
                                                    Files.move(tmpDir.resolve("tmp-root-${i}"), dir.resolve(String.format("root-file-%02d.txt", i)))
                                                    Files.move(tmpSubDir.resolve("tmp-sub-${i}"), finalSubDir.resolve(String.format("sub-file-%02d.txt", i)))
                                                }
                                                tmpDir.deleteDir()
                                            }
    }
}
