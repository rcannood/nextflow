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
    def 'hash should be consistent when using filesystem trick: #description'() {
        given: "Two directories to be filled with identical content using different strategies"
        Path dir1 = TestHelper.createInMemTempDir()
        Path dir2 = TestHelper.createInMemTempDir()

        and: "A 'control' setup creating files alphabetically"
        def controlSetup = { Path dir ->
            dir.resolve('a.txt').text = 'content-a'
            dir.resolve('b.txt').text = 'content-b'
            dir.resolve('subdir').mkdir()
            dir.resolve('subdir/c.txt').text = 'content-c'
            dir.resolve('z.txt').text = 'content-z'
        }

        and: "An 'experimental' setup that applies a filesystem trick"
        def experimentalSetup = setupClosure

        // Create the two directories
        controlSetup(dir1)
        experimentalSetup(dir2)

        when: "Hashes for both directories are calculated"
        def hasher1 = HashBuilder.defaultHasher()
        HashBuilder.hashDirSha256(hasher1, dir1, dir1)
        def hash1 = hasher1.hash()

        def hasher2 = HashBuilder.defaultHasher()
        HashBuilder.hashDirSha256(hasher2, dir2, dir2)
        def hash2 = hasher2.hash()

        then: "The hashes must be identical, as the new code sorts paths internally"
        hash1 == hash2

        where:
        description                      | setupClosure
        'Reverse Creation Order'         | { Path dir ->
                                                // Technique: Create files in the reverse order of the control setup.
                                                // Goal: On simple filesystems, entry order might follow creation order.
                                                dir.resolve('z.txt').text = 'content-z'
                                                dir.resolve('subdir').mkdir()
                                                dir.resolve('subdir/c.txt').text = 'content-c'
                                                dir.resolve('b.txt').text = 'content-b'
                                                dir.resolve('a.txt').text = 'content-a'
                                            }

        'Delete and Re-create Middle'    | { Path dir ->
                                                // Technique: Delete an entry from the middle of an alphabetical set and re-add it.
                                                // Goal: The re-added entry might be appended to the end of the directory list.
                                                dir.resolve('a.txt').text = 'content-a'
                                                dir.resolve('b.txt').text = 'content-b'
                                                dir.resolve('subdir').mkdir()
                                                dir.resolve('subdir/c.txt').text = 'content-c'
                                                dir.resolve('z.txt').text = 'content-z'
                                                // Now, delete and re-create 'b.txt'
                                                dir.resolve('b.txt').delete()
                                                dir.resolve('b.txt').text = 'content-b'
                                            }

        'Rename-Shuffle'                 | { Path dir ->
                                                // Technique: Create files with temporary names and rename them in a non-alphabetical order.
                                                // Goal: The final directory entry order might be influenced by the timing of the rename operations.
                                                def tmpA = dir.resolve('tmp-a')
                                                def tmpB = dir.resolve('tmp-b')
                                                def tmpS = dir.resolve('tmp-s')
                                                def tmpZ = dir.resolve('tmp-z')
                                                // Create temp files/dirs
                                                tmpA.text = 'content-a'
                                                tmpB.text = 'content-b'
                                                tmpS.mkdir()
                                                tmpS.resolve('c.txt').text = 'content-c'
                                                tmpZ.text = 'content-z'
                                                // Rename in reverse order
                                                Files.move(tmpZ, dir.resolve('z.txt'))
                                                Files.move(tmpS, dir.resolve('subdir'))
                                                Files.move(tmpB, dir.resolve('b.txt'))
                                                Files.move(tmpA, dir.resolve('a.txt'))
                                            }
    }
}
