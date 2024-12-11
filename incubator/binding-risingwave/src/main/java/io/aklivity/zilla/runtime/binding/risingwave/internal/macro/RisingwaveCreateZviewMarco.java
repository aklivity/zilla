package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

public class RisingwaveCreateZviewMarco
{
    private interface CreateZviewState
    {
        void handleEvent(
            RisingwaveCreateZviewMarco context,
            MacroEvent event);
    }

    public enum MacroEvent
    {
        QUERY,
        COMPLETED,
        READY,
        ERROR
    }

    private CreateZviewState currentState;

    public RisingwaveCreateZviewMarco()
    {
        this.currentState = new CreateMaterializedVievState();
    }

    public void setState(CreateZviewState state)
    {
        this.currentState = state;
    }

    public void handleEvent(
        MacroEvent event)
    {
        currentState.handleEvent(this, event);
    }


    private class CreateMaterializedVievState implements CreateZviewState
    {
        @Override
        public void handleEvent(
            RisingwaveCreateZviewMarco context,
            MacroEvent event)
        {
            switch (event)
            {
            case QUERY:

                context.setState(new QueryState());
                break;
            case COMPLETED:
                context.setState(new LockedState());
                break;
            case READY:
                context.setState(new LockedState());
                break;
            case ERROR:
                context.setState();
                break;
            }
        }

        private void doSendQuery()
        {
            //NOOP
        }
    }

    private class QueryState implements CreateZviewState
    {
        @Override
        public void handleEvent(
            RisingwaveCreateZviewMarco context,
            MacroEvent event)
        {
            //NOOP
        }
    }

}
