package handler

import (
	"errors"
	"testing"
)

func TestPendingStreamErrorPrefersBufferedError(t *testing.T) {
	expected := errors.New("stream failed")
	errCh := make(chan error, 1)
	errCh <- expected
	close(errCh)

	actual, ok := pendingStreamError(errCh)
	if !ok {
		t.Fatal("expected pending error")
	}
	if actual != expected {
		t.Fatalf("expected %v, got %v", expected, actual)
	}
}

func TestPendingStreamErrorIgnoresClosedEmptyChannel(t *testing.T) {
	errCh := make(chan error)
	close(errCh)

	if err, ok := pendingStreamError(errCh); ok || err != nil {
		t.Fatalf("expected no pending error, got ok=%v err=%v", ok, err)
	}
}
