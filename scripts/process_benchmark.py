import json

with open("/home/vipul/Workspace/TIFR/Code/my-source/benchmark_CAV22_mdp.json") as file:
    result = json.load(file)

with open(
    "/home/vipul/Workspace/TIFR/Code/my-source/benchmark_CAV22_mdp.csv", "w"
) as file:
    file.write(
        "model_name, lower_bound, upper_bound, value, time_take, states_explored\n"
    )

    for k, v in result.items():
        model_name = "".join(k.split(" ")[2].split("/")[-1].split(".")[:-1])

        lower_bound = 0
        upper_bound = 0
        time_taken = 0
        value = 0
        states_explored = 0
        for i in v:
            value += i["value"]
            lower_bound += i["bounds"][0]
            upper_bound += i["bounds"][1]
            time_taken += i["time_taken"]
            states_explored += i["states_explored"]

        lower_bound /= 5
        upper_bound /= 5
        time_taken /= 5
        value /= 5
        states_explored //= 5

        print(
            f"{model_name:>20}, {lower_bound:.4}, {upper_bound:.4}, {value:.4}, {time_taken:.4}, {states_explored}"
        )
        file.write(
            f"{model_name}, {lower_bound}, {upper_bound}, {value}, {time_taken}, {states_explored}\n"
        )
