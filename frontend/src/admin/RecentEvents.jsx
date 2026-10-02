import { motion } from "framer-motion";

import {
  UserPlus,
  Server,
  Database,
  ShieldAlert,
  ShieldCheck,
  Activity,
  ArrowUpCircle,
} from "lucide-react";

const iconMap = {
  user: UserPlus,
  vm: Server,
  storage: Database,
  security: ShieldAlert,
  login: ShieldCheck,
  scaling: ArrowUpCircle,
  monitoring: Activity,
};

const colorMap = {
  success: "bg-green-100 text-green-600",
  warning: "bg-yellow-100 text-yellow-600",
  danger: "bg-red-100 text-red-600",
  info: "bg-blue-100 text-blue-600",
};

const RecentEvents = ({ events = [] }) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-white rounded-2xl shadow-lg border border-gray-200 p-6"
    >
      {/* Header */}

      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Recent Events</h2>

          <p className="text-gray-500 mt-1">
            Latest activities across CloudSphere
          </p>
        </div>

        <span className="text-sm text-green-600 font-semibold">● Live</span>
      </div>

      {/* Timeline */}

      <div className="space-y-5">
        {events.map((event, index) => {
          const Icon = iconMap[event.type] || Activity;

          return (
            <motion.div
              key={index}
              initial={{ opacity: 0, x: -15 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.08 }}
              whileHover={{ x: 5 }}
              className="flex items-start gap-4"
            >
              {/* Icon */}

              <div
                className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                  colorMap[event.status] || colorMap.info
                }`}
              >
                <Icon size={22} />
              </div>

              {/* Content */}

              <div className="flex-1">
                <div className="flex justify-between items-center">
                  <h4 className="font-semibold text-slate-800">
                    {event.title}
                  </h4>

                  <span className="text-xs text-gray-400">{event.time}</span>
                </div>

                <p className="text-gray-500 mt-1">{event.description}</p>

                <p className="text-sm mt-2 text-blue-600 font-medium">
                  {event.user}
                </p>
              </div>
            </motion.div>
          );
        })}

        {events.length === 0 && (
          <div className="text-center py-10 text-gray-400">
            No recent events available.
          </div>
        )}
      </div>
    </motion.div>
  );
};

export default RecentEvents;
