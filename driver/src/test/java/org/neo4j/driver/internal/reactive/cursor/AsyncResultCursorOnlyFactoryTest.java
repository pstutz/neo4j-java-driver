/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
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
package org.neo4j.driver.internal.reactive.cursor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.neo4j.driver.internal.AsyncStatementResultCursor;
import org.neo4j.driver.internal.handlers.AbstractPullAllResponseHandler;
import org.neo4j.driver.internal.handlers.RunResponseHandler;
import org.neo4j.driver.internal.messaging.Message;
import org.neo4j.driver.internal.spi.Connection;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.driver.internal.messaging.request.PullAllMessage.PULL_ALL;
import static org.neo4j.driver.internal.util.Futures.completedWithNull;
import static org.neo4j.driver.internal.util.Futures.failedFuture;
import static org.neo4j.driver.internal.util.Futures.getNow;

class AsyncResultCursorOnlyFactoryTest
{
    private static Stream<Boolean> waitForRun()
    {
        return Stream.of( true, false );
    }

    // asyncResult
    @ParameterizedTest
    @MethodSource( "waitForRun" )
    void shouldReturnAsyncResultWhenRunSucceeded( boolean waitForRun ) throws Throwable
    {
        // Given
        Connection connection = mock( Connection.class );
        StatementResultCursorFactory cursorFactory = newResultCursorFactory( connection, completedWithNull(), waitForRun );

        // When
        CompletionStage<InternalStatementResultCursor> cursorFuture = cursorFactory.asyncResult();

        // Then

        verifyRunCompleted( connection, cursorFuture );
    }

    @ParameterizedTest
    @MethodSource( "waitForRun" )
    void shouldReturnAsyncResultWhenRunCompletedWithFailure( boolean waitForRun ) throws Throwable
    {
        // Given
        Connection connection = mock( Connection.class );
        Throwable error = new RuntimeException( "Hi there" );
        StatementResultCursorFactory cursorFactory = newResultCursorFactory( connection, completedFuture( error ), waitForRun );

        // When
        CompletionStage<InternalStatementResultCursor> cursorFuture = cursorFactory.asyncResult();

        // Then
        verifyRunCompleted( connection, cursorFuture );
    }

    @Test
    void shouldFailAsyncResultWhenRunFailed() throws Throwable
    {
        // Given
        Throwable error = new RuntimeException( "Hi there" );
        StatementResultCursorFactory cursorFactory = newResultCursorFactory( failedFuture( error ), true );

        // When
        CompletionStage<InternalStatementResultCursor> cursorFuture = cursorFactory.asyncResult();

        // Then
        CompletionException actual = assertThrows( CompletionException.class, () -> getNow( cursorFuture ) );
        assertThat( actual.getCause(), equalTo( error ) );
    }

    @Test
    void shouldNotFailAsyncResultEvenWhenRunFailed() throws Throwable
    {
        // Given
        Connection connection = mock( Connection.class );
        Throwable error = new RuntimeException( "Hi there" );
        StatementResultCursorFactory cursorFactory = newResultCursorFactory( connection, failedFuture( error ), false );

        // When
        CompletionStage<InternalStatementResultCursor> cursorFuture = cursorFactory.asyncResult();

        // Then
        verifyRunCompleted( connection, cursorFuture );
    }

    // rxResult
    @ParameterizedTest
    @MethodSource( "waitForRun" )
    void shouldErrorForRxResult( boolean waitForRun ) throws Throwable
    {
        // Given
        StatementResultCursorFactory cursorFactory = newResultCursorFactory( completedWithNull(), waitForRun );

        // When & Then
        CompletionStage<RxStatementResultCursor> rxCursorFuture = cursorFactory.rxResult();
        CompletionException error = assertThrows( CompletionException.class, () -> getNow( rxCursorFuture ) );
        assertThat( error.getCause().getMessage(), containsString( "Driver is connected to the database that does not support driver reactive API" ) );
    }

    private AsyncResultCursorOnlyFactory newResultCursorFactory( Connection connection, CompletableFuture<Throwable> runFuture, boolean waitForRun )
    {
        Message runMessage = mock( Message.class );

        RunResponseHandler runHandler = mock( RunResponseHandler.class );
        when( runHandler.runFuture() ).thenReturn( runFuture );

        AbstractPullAllResponseHandler pullHandler = mock( AbstractPullAllResponseHandler.class );

        return new AsyncResultCursorOnlyFactory( connection, runMessage, runHandler, pullHandler, waitForRun );
    }

    private AsyncResultCursorOnlyFactory newResultCursorFactory( CompletableFuture<Throwable> runFuture, boolean waitForRun )
    {
        Connection connection = mock( Connection.class );
        return newResultCursorFactory( connection, runFuture, waitForRun );
    }

    private void verifyRunCompleted( Connection connection, CompletionStage<InternalStatementResultCursor> cursorFuture )
    {
        verify( connection ).writeAndFlush( any( Message.class ), any( RunResponseHandler.class ), eq( PULL_ALL ),
                any( AbstractPullAllResponseHandler.class ) );
        assertThat( getNow( cursorFuture ), instanceOf( AsyncStatementResultCursor.class ) );
    }
}
