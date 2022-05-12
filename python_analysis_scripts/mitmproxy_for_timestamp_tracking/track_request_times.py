from mitmproxy.script import concurrent
from mitmproxy import ctx


class FlooHelper:
    def __init__(self):
        self.output_file = open("timestamps_bbc_oct20.txt", "a")

    def done(self):
        self.output_file.close()

    def response(self, flow):
        print(f"some resposne is coming")
        if flow.response.status_code < 300:
            # try:
            #     self.output_file.write(f"start={flow.request.timestamp_start},end={flow.request.timestamp_end},type={flow.response.headers['content-type']}\n")
            # except Exception as e:
            #     pass
            try:
                floo_timestamp=int(flow.request.headers['FLOO'])/1000.0 # convert to seconds
                print(f"url={flow.request.pretty_host},start={flow.request.timestamp_start},end={flow.request.timestamp_end},floo_header={floo_timestamp},diff={flow.request.timestamp_start-floo_timestamp} seconds\n")
            except Exception as e:
                print(e)


addons = [
    FlooHelper()
]