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

package test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import groovy.transform.Memoized
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TestHelper {

    static <T> T proxyFor(Class<T> clazz) {

        def holder = [:]
        def proxy = {}.asType(clazz)
        proxy.metaClass.getProperty = { name -> holder.get(name) }
        proxy.metaClass.setProperty = { name, value -> holder.put(name,value) }
        proxy.metaClass.invokeMethod = { String name, Object args ->
            if( name.startsWith('set') && name.size()>3 && args?.size()==1 ) {
                def key = name.substring(3,4).toLowerCase()
                if( name.size()>4 ) key += name.substring(4)
                holder.put(key,args[0])
            }
            else if( name.startsWith('get') && name.size()>3 && args?.size()==0 ) {
                def key = name.substring(3,4).toLowerCase()
                if( name.size()>4 ) key += name.substring(4)
                holder.get(key)
            }

        }

        return proxy
    }

    static private fs = Jimfs.newFileSystem(Configuration.unix());

    static Path createInMemTempDir() {
        Path tmp = fs.getPath("/tmp");
        tmp.mkdir()
        Files.createTempDirectory(tmp, 'test')
    }

    static Path createInMemTempFile(String name='temp.file', String content=null) {
        Path tmp = fs.getPath("/tmp");
        tmp.mkdir()
        def result = Files.createTempDirectory(tmp, 'test').resolve(name)
        if( content )
            result.text = content
        return result
    }

    @Memoized
    static boolean graphvizInstalled() {
        ["bash","-c","command -v dot &>/dev/null"].execute().waitFor() == 0
    }


    static void stopUntil( Closure<Boolean> condition ) {
        def start = System.currentTimeMillis()
        while( !condition.call() && (System.currentTimeMillis()-start) < 90_000 ) {
            sleep 100
        }
    }

    static String gunzip(Path path) {
        def file = new GZIPInputStream(new FileInputStream(path.toFile()));
        file.text
    }

    static String decodeBase64(String encoded) {
        byte[] decodedBytes = Base64.getDecoder().decode(encoded);
        // Convert the decoded bytes into a string
        return new String(decodedBytes);
    }

    static int rndServerPort() {
        ServerSocket socket = new ServerSocket(0)
        int port = socket.localPort
        socket.close()
        return port
    }
}
