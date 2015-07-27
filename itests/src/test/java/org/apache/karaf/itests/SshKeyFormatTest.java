/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.karaf.itests;

import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.ServerKeyVerifier;
import org.apache.sshd.client.future.ConnectFuture;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import java.io.File;
import java.net.SocketAddress;
import java.security.PublicKey;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

/**
 * Test use of PEM keys.
 */
public class SshKeyFormatTest extends SshCommandTestBase {

    @Configuration
    public Option[] config() {
        File keyFile = new File("src/test/resources/etc/test.pem");
        return options(composite(super.config()),
                editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "hostKey", keyFile.getAbsolutePath()),
                editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "hostKeyFormat", "PEM")
//                ,
//                vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
                );
    }

    @Test
    public void usePemKey() throws Exception {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(new ServerKeyVerifier() {
            @Override
            public boolean verifyServerKey(ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) {
                System.err.println(serverKey.getAlgorithm());
                System.err.println(serverKey.getFormat());
                StringBuilder dump = new StringBuilder();
                for (byte b : serverKey.getEncoded()) {
                    dump.append(String.format("%02x", b));
                }
                System.err.println(dump.toString());
                return true;
            }
        });
        client.start();
        ConnectFuture future = client.connect("karaf", "localhost", 8101).await();
        ClientSession session = future.getSession();
        int ret = ClientSession.WAIT_AUTH;
        while ((ret & ClientSession.WAIT_AUTH) != 0) {
            session.addPasswordIdentity("karaf");
            session.auth().verify();
            ret = session.waitFor(ClientSession.WAIT_AUTH | ClientSession.CLOSED | ClientSession.AUTHED, 0);
        }
        if ((ret & ClientSession.CLOSED) != 0) {
            System.err.format("ret %d%n", ret);
            throw new Exception("Could not open SSH channel");
        }
        session.close(true);
    }
}
